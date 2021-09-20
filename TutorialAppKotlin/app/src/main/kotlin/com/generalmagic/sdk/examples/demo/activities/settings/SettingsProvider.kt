/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.settings

import com.generalmagic.sdk.core.EServiceGroupType
import com.generalmagic.sdk.core.EUnitSystem
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.SettingsService
import com.generalmagic.sdk.routesandnavigation.ERouteTransportMode
import com.generalmagic.sdk.routesandnavigation.ERouteType
import com.generalmagic.sdk.routesandnavigation.RoutePreferences
import com.generalmagic.sdk.util.EnumHelp
import com.generalmagic.sdk.util.SdkCall

enum class TBoolSettings(val value: Int) {
    EBooleanSettingsBase(0),
    EUseMobileData(1),
    EUseMobileDataForTraffic(2),
    EUseMobileDataForMapAndWikipedia(3),
    EUseMobileDataForTerrainAndSatellite(4),
    EAvoidMotorwaysCar(5),
    EAvoidTollRoadsCar(6),
    EAvoidFerriesCar(7),
    EAvoidUnpavedRoadsCar(8),
    ERecordAudio(9),
    EBooleanSettingsCount(10)
}

enum class TIntSettings(val value: Int) {
    EIntSettingsBase(TBoolSettings.EBooleanSettingsCount.value + 1),
    EMapViewAngle(EIntSettingsBase.value + 1),
    EZoomLevel(EMapViewAngle.value + 1),
    ETravelModeCar(EZoomLevel.value + 1),
    EDemoSpeed(ETravelModeCar.value + 1),
    EUnitsSystem(EDemoSpeed.value + 1),
    ERecordingChunk(EUnitsSystem.value + 1),
    EMinMinutes(ERecordingChunk.value + 1),
    EDiskLimit(EMinMinutes.value + 1),
    EIntSettingsCount(EDiskLimit.value + 1)
}

enum class TDoubleSettings(val value: Int) {
    EDoubleSettingsBase(TIntSettings.EIntSettingsCount.value + 1),
    ELatitude(EDoubleSettingsBase.value + 1),
    ELongitude(ELatitude.value + 1),
    EMapStyle(ELongitude.value + 1),
    EDoubleSettingsCount(EMapStyle.value + 1)
}

enum class TStringSettings(val value: Int) {
    EStringSettingsBase(TDoubleSettings.EDoubleSettingsCount.value + 1),
    EVoice(EStringSettingsBase.value + 1),
    EAddressSearchCountryISOCode(EVoice.value + 1),
    EStringSettingsCount(EVoice.value + 1)
}

@Suppress("unused")
enum class TSettingsLimit(val value: Int) {
    ESettingsCount(TStringSettings.EStringSettingsCount.value)
}

object SettingsProvider {
    private var mService: SettingsService? = null
    private val mNames = Array(TStringSettings.EStringSettingsCount.value) { "" }

    private val mBoolDefaultValues =
        BooleanArray(TBoolSettings.EBooleanSettingsCount.value) { false }
    private val mIntDefaultValues = IntArray(TIntSettings.EIntSettingsCount.value) { 0 }
    private val mDoubleDefaultValues =
        DoubleArray(TDoubleSettings.EDoubleSettingsCount.value) { 0.0 }
    private val mStringDefaultValues = Array(TStringSettings.EStringSettingsCount.value) { "" }

    private val mBoolCallbacks =
        Array<((Boolean) -> Unit)?>(TBoolSettings.EBooleanSettingsCount.value) { null }
    private val mIntCallbacks =
        Array<((Int) -> Unit)?>(TIntSettings.EIntSettingsCount.value) { null }
    private val mDoubleCallbacks =
        Array<((Double) -> Unit)?>(TDoubleSettings.EDoubleSettingsCount.value) { null }

    init {
        SdkCall.execute {
            mService = SettingsService.produce("Settings.ini")
            setNames()
            setDefaultValues()
            setCallbacks()
        }
    }

    /* <Success, Value> */
    fun getBooleanValue(setting: Int): Pair<Boolean, Boolean> {
        SdkCall.checkCurrentThread()

        val service = this.mService

        var result = Pair(false, mBoolDefaultValues[setting])
        if ((setting > TBoolSettings.EBooleanSettingsBase.value) &&
            (setting < TBoolSettings.EBooleanSettingsCount.value) &&
            service != null
        ) {
            result = Pair(
                true, service.getBooleanValue(mNames[setting], mBoolDefaultValues[setting])
            )
        }

        return result
    }

    fun setBooleanValue(setting: Int, value: Boolean) {
        SdkCall.checkCurrentThread()

        val service = this.mService

        if ((setting > TBoolSettings.EBooleanSettingsBase.value) &&
            (setting < TBoolSettings.EBooleanSettingsCount.value) &&
            service != null
        ) {
            service.setBooleanValue(mNames[setting], value)
            val lambda = mBoolCallbacks[setting]
            if (lambda != null) {
                lambda(value)
            }
        }
    }

    fun setIntValue(setting: Int, value: Int) {
        SdkCall.checkCurrentThread()

        val service = this.mService

        if ((setting > TIntSettings.EIntSettingsBase.value) &&
            (setting < TIntSettings.EIntSettingsCount.value) &&
            service != null
        ) {
            service.setIntValue(mNames[setting], value)
            val lambda = mIntCallbacks[setting]
            if (lambda != null) {
                lambda(value)
            }
        }
    }

    fun getIntValue(setting: Int): Pair<Boolean, Int> {
        SdkCall.checkCurrentThread()

        val service = this.mService

        var result = Pair(false, 0)
        if ((setting > TIntSettings.EIntSettingsBase.value) &&
            (setting < TIntSettings.EIntSettingsCount.value) &&
            service != null
        ) {
            result = Pair(
                true, service.getIntValue(mNames[setting], mIntDefaultValues[setting])
            )
        }

        return result
    }

    @Suppress("unused")
    fun getDoubleValue(setting: Int): Pair<Boolean, Double> {
        SdkCall.checkCurrentThread()

        val service = this.mService

        var result = Pair(false, 0.0)
        if ((setting > TDoubleSettings.EDoubleSettingsBase.value) &&
            (setting < TDoubleSettings.EDoubleSettingsCount.value) &&
            service != null
        ) {
            result = Pair(
                true, service.getDoubleValue(mNames[setting], mDoubleDefaultValues[setting])
            )
        }

        return result
    }

    @Suppress("unused")
    fun setDoubleValue(setting: Int, value: Double) {
        SdkCall.checkCurrentThread()

        val service = this.mService

        if ((setting > TDoubleSettings.EDoubleSettingsBase.value) &&
            (setting < TDoubleSettings.EDoubleSettingsCount.value) &&
            service != null
        ) {
            service.setDoubleValue(mNames[setting], value)
            val lambda = mDoubleCallbacks[setting]
            if (lambda != null) {
                lambda(value)
            }
        }
    }

    private fun setNames() {
        // bool settings

        mNames[TBoolSettings.EUseMobileData.value] = "use mobile data"
        mNames[TBoolSettings.EUseMobileDataForTraffic.value] = "use mobile data for traffic"
        mNames[TBoolSettings.EUseMobileDataForMapAndWikipedia.value] =
            "use mobile data for map and wikipedia"
        mNames[TBoolSettings.EUseMobileDataForTerrainAndSatellite.value] =
            "use mobile data for terrain and satellite"
        mNames[TBoolSettings.EAvoidMotorwaysCar.value] = "avoid motorways car"
        mNames[TBoolSettings.EAvoidTollRoadsCar.value] = "avoid toll roads car"
        mNames[TBoolSettings.EAvoidFerriesCar.value] = "avoid ferries car"
        mNames[TBoolSettings.EAvoidUnpavedRoadsCar.value] = "avoid unpaved roads car"
        mNames[TBoolSettings.ERecordAudio.value] = "recording audio"

        // int settings

        mNames[TIntSettings.EMapViewAngle.value] = "map view angle"
        mNames[TIntSettings.EZoomLevel.value] = "zoom level"
        mNames[TIntSettings.ETravelModeCar.value] = "travel mode car"
        mNames[TIntSettings.EDemoSpeed.value] = "demo speed"
        mNames[TIntSettings.EUnitsSystem.value] = "units system"
        mNames[TIntSettings.ERecordingChunk.value] = "recording chunk"
        mNames[TIntSettings.EMinMinutes.value] = "min minutes to keep"
        mNames[TIntSettings.EDiskLimit.value] = "recording disk limit"

        // double settings

        mNames[TDoubleSettings.ELatitude.value] = "latitude"
        mNames[TDoubleSettings.ELongitude.value] = "longitude"
        mNames[TDoubleSettings.EMapStyle.value] = "map style"

        // string settings

        mNames[TStringSettings.EVoice.value] = "voice"
    }

    private fun setDefaultValues() {
        // bool settings

        mBoolDefaultValues[TBoolSettings.EUseMobileData.value] = true
        mBoolDefaultValues[TBoolSettings.EUseMobileDataForTraffic.value] = true
        mBoolDefaultValues[TBoolSettings.EUseMobileDataForMapAndWikipedia.value] = true
        mBoolDefaultValues[TBoolSettings.EUseMobileDataForTerrainAndSatellite.value] = false
        mBoolDefaultValues[TBoolSettings.EAvoidMotorwaysCar.value] = false
        mBoolDefaultValues[TBoolSettings.EAvoidTollRoadsCar.value] = false
        mBoolDefaultValues[TBoolSettings.EAvoidFerriesCar.value] = false
        mBoolDefaultValues[TBoolSettings.EAvoidUnpavedRoadsCar.value] = true

        // int settings

        mIntDefaultValues[TIntSettings.EMapViewAngle.value - TIntSettings.EIntSettingsBase.value] =
            0
        mIntDefaultValues[TIntSettings.EZoomLevel.value - TIntSettings.EIntSettingsBase.value] = 80
        mIntDefaultValues[TIntSettings.ETravelModeCar.value - TIntSettings.EIntSettingsBase.value] =
            ERouteType.Fastest.value
        mIntDefaultValues[TIntSettings.EDemoSpeed.value - TIntSettings.EIntSettingsBase.value] = 1
        mIntDefaultValues[TIntSettings.EUnitsSystem.value - TIntSettings.EIntSettingsBase.value] =
            EUnitSystem.Metric.value

        // double settings

        mDoubleDefaultValues[TDoubleSettings.ELatitude.value - TDoubleSettings.EDoubleSettingsBase.value] =
            Double.MAX_VALUE
        mDoubleDefaultValues[TDoubleSettings.ELongitude.value - TDoubleSettings.EDoubleSettingsBase.value] =
            Double.MAX_VALUE
        mDoubleDefaultValues[TDoubleSettings.EMapStyle.value - TDoubleSettings.EDoubleSettingsBase.value] =
            0.0

        // string settings

        mStringDefaultValues[TStringSettings.EVoice.value - TStringSettings.EStringSettingsBase.value] =
            ""
        mStringDefaultValues[TStringSettings.EAddressSearchCountryISOCode.value - TStringSettings.EStringSettingsBase.value] =
            ""

        // Recorder

        mIntDefaultValues[TIntSettings.ERecordingChunk.value] = 5 //5 min
        mBoolDefaultValues[TBoolSettings.ERecordAudio.value] = false
        mIntDefaultValues[TIntSettings.EMinMinutes.value] = 30 //30 min
        mIntDefaultValues[TIntSettings.EDiskLimit.value] = 1000 //1gb
    }

    private fun setCallbacks() {
        SdkCall.checkCurrentThread()
        // bool settings

        val useMobileData = { value: Boolean ->
            if (mService != null) {
                if (value) {
                    val bUseMobileDataForTraffic =
                        getBooleanValue(TBoolSettings.EUseMobileDataForTraffic.value)
                    val bUseMobileDataForMapAndWikipedia =
                        getBooleanValue(TBoolSettings.EUseMobileDataForMapAndWikipedia.value)
                    val bUseMobileDataForTerrainAndSatellite =
                        getBooleanValue(TBoolSettings.EUseMobileDataForTerrainAndSatellite.value)

                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.TrafficService, bUseMobileDataForTraffic.second
                    )
                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.MapDataService, bUseMobileDataForMapAndWikipedia.second
                    )
                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.TerrainService,
                        bUseMobileDataForTerrainAndSatellite.second
                    )
                } else {
                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.TrafficService, value
                    )
                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.MapDataService, value
                    )
                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.TerrainService, value
                    )
                }
            }
        }
        mBoolCallbacks[TBoolSettings.EUseMobileData.value] = useMobileData

        val useMobileDataForTraffic = { value: Boolean ->
            if (mService != null) {
                SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                    EServiceGroupType.TrafficService, value
                )
            }
        }
        mBoolCallbacks[TBoolSettings.EUseMobileDataForTraffic.value] = useMobileDataForTraffic

        val useMobileDataForMapAndWikipedia = { value: Boolean ->
            if (mService != null) {
                SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                    EServiceGroupType.MapDataService,
                    value
                )
                if (value) {
                    val bUseMobileDataForTerrainAndSatellite =
                        getBooleanValue(TBoolSettings.EUseMobileDataForTerrainAndSatellite.value)

                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.TerrainService,
                        bUseMobileDataForTerrainAndSatellite.second
                    )
                } else {
                    SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.TerrainService, value
                    )
                }
            }
        }
        mBoolCallbacks[TBoolSettings.EUseMobileDataForMapAndWikipedia.value] =
            useMobileDataForMapAndWikipedia

        val useMobileDataForTerrainAndSatellite = { value: Boolean ->
            if (mService != null) {
                SdkSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                    EServiceGroupType.TerrainService,
                    value
                )
            }
        }
        mBoolCallbacks[TBoolSettings.EUseMobileDataForTerrainAndSatellite.value] =
            useMobileDataForTerrainAndSatellite

        // int settings

        val unitsSystem = { value: Int ->
            if (mService != null) {
                SdkSettings().unitSystem = EnumHelp.fromInt(value)
            }
        }
        mIntCallbacks[TIntSettings.EUnitsSystem.value - TIntSettings.EIntSettingsBase.value] =
            unitsSystem

    }


    fun loadRoutePreferences(): RoutePreferences {
        SdkCall.checkCurrentThread()

        val routeTypeSetting = getIntValue(TIntSettings.ETravelModeCar.value)
        val avoidMotorwaysSetting = getBooleanValue(TBoolSettings.EAvoidMotorwaysCar.value)
        val avoidTollSetting = getBooleanValue(TBoolSettings.EAvoidTollRoadsCar.value)
        val avoidFerriesSetting = getBooleanValue(TBoolSettings.EAvoidFerriesCar.value)
        val avoidUnpavedSetting = getBooleanValue(TBoolSettings.EAvoidUnpavedRoadsCar.value)

        val preferences = RoutePreferences()
        preferences.transportMode = ERouteTransportMode.Car
        preferences.routeType = EnumHelp.fromInt(routeTypeSetting.second)
        preferences.avoidTollRoads = avoidTollSetting.second
        preferences.avoidMotorways = avoidMotorwaysSetting.second
        preferences.avoidFerries = avoidFerriesSetting.second
        preferences.avoidUnpavedRoads = avoidUnpavedSetting.second

        return preferences
    }
}
