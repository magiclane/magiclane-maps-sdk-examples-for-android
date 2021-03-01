/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.settings

import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.sdk.core.CommonSettings
import com.generalmagic.sdk.core.EServiceGroupType
import com.generalmagic.sdk.core.EUnitSystem
import com.generalmagic.sdk.core.SettingsService
import com.generalmagic.sdk.routingandnavigation.ERouteTransportMode
import com.generalmagic.sdk.routingandnavigation.ERouteType
import com.generalmagic.sdk.routingandnavigation.RoutePreferences
import com.generalmagic.sdk.util.GEMSdkCall

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

enum class TSettingsLimit(value: Int) {
    ESettingsCount(TStringSettings.EStringSettingsCount.value)
}

object SettingsProvider {
    var m_service: SettingsService? = null
    val m_names = Array(TStringSettings.EStringSettingsCount.value) { "" }

    val m_boolDefaultValues = BooleanArray(TBoolSettings.EBooleanSettingsCount.value) { false }
    val m_intDefaultValues = IntArray(TIntSettings.EIntSettingsCount.value) { 0 }
    val m_doubleDefaultValues = DoubleArray(TDoubleSettings.EDoubleSettingsCount.value) { 0.0 }
    val m_stringDefaultValues = Array(TStringSettings.EStringSettingsCount.value) { "" }

    val m_boolCallbacks =
        Array<((Boolean) -> Unit)?>(TBoolSettings.EBooleanSettingsCount.value) { null }
    val m_intCallbacks = Array<((Int) -> Unit)?>(TIntSettings.EIntSettingsCount.value) { null }
    val m_doubleCallbacks =
        Array<((Double) -> Unit)?>(TDoubleSettings.EDoubleSettingsCount.value) { null }

    init {
        GEMSdkCall.execute {
            m_service = SettingsService.produce("Settings.ini")
            setNames()
            setDefaultValues()
            setCallbacks()
        }
    }

    /* <Success, Value> */
    fun getBooleanValue(setting: Int): Pair<Boolean, Boolean> {
        GEMSdkCall.checkCurrentThread()

        val service = this.m_service

        var result = Pair(false, m_boolDefaultValues[setting])
        if ((setting > TBoolSettings.EBooleanSettingsBase.value) &&
            (setting < TBoolSettings.EBooleanSettingsCount.value) &&
            service != null
        ) {
            result = Pair(
                true, service.getBooleanValue(m_names[setting], m_boolDefaultValues[setting])
            )
        }

        return result
    }

    fun setBooleanValue(setting: Int, value: Boolean) {
        GEMSdkCall.checkCurrentThread()

        val service = this.m_service

        if ((setting > TBoolSettings.EBooleanSettingsBase.value) &&
            (setting < TBoolSettings.EBooleanSettingsCount.value) &&
            service != null
        ) {
            service.setBooleanValue(m_names[setting], value)
            val lambda = m_boolCallbacks[setting]
            if (lambda != null) {
                lambda(value)
            }
        }
    }

    fun setIntValue(setting: Int, value: Int) {
        GEMSdkCall.checkCurrentThread()

        val service = this.m_service

        if ((setting > TIntSettings.EIntSettingsBase.value) &&
            (setting < TIntSettings.EIntSettingsCount.value) &&
            service != null
        ) {
            service.setIntValue(m_names[setting], value)
            val lambda = m_intCallbacks[setting]
            if (lambda != null) {
                lambda(value)
            }
        }
    }

    fun getIntValue(setting: Int): Pair<Boolean, Int> {
        GEMSdkCall.checkCurrentThread()

        val service = this.m_service

        var result = Pair(false, 0)
        if ((setting > TIntSettings.EIntSettingsBase.value) &&
            (setting < TIntSettings.EIntSettingsCount.value) &&
            service != null
        ) {
            result = Pair(
                true, service.getIntValue(m_names[setting], m_intDefaultValues[setting])
            )
        }

        return result
    }

    fun getDoubleValue(setting: Int): Pair<Boolean, Double> {
        GEMSdkCall.checkCurrentThread()

        val service = this.m_service

        var result = Pair(false, 0.0)
        if ((setting > TDoubleSettings.EDoubleSettingsBase.value) &&
            (setting < TDoubleSettings.EDoubleSettingsCount.value) &&
            service != null
        ) {
            result = Pair(
                true, service.getDoubleValue(m_names[setting], m_doubleDefaultValues[setting])
            )
        }

        return result
    }

    fun setDoubleValue(setting: Int, value: Double) {
        GEMSdkCall.checkCurrentThread()

        val service = this.m_service

        if ((setting > TDoubleSettings.EDoubleSettingsBase.value) &&
            (setting < TDoubleSettings.EDoubleSettingsCount.value) &&
            service != null
        ) {
            service.setDoubleValue(m_names[setting], value)
            val lambda = m_doubleCallbacks[setting]
            if (lambda != null) {
                lambda(value)
            }
        }
    }

    private fun setNames() {
        // bool settings

        m_names[TBoolSettings.EUseMobileData.value] = "use mobile data"
        m_names[TBoolSettings.EUseMobileDataForTraffic.value] = "use mobile data for traffic"
        m_names[TBoolSettings.EUseMobileDataForMapAndWikipedia.value] =
            "use mobile data for map and wikipedia"
        m_names[TBoolSettings.EUseMobileDataForTerrainAndSatellite.value] =
            "use mobile data for terrain and satellite"
        m_names[TBoolSettings.EAvoidMotorwaysCar.value] = "avoid motorways car"
        m_names[TBoolSettings.EAvoidTollRoadsCar.value] = "avoid toll roads car"
        m_names[TBoolSettings.EAvoidFerriesCar.value] = "avoid ferries car"
        m_names[TBoolSettings.EAvoidUnpavedRoadsCar.value] = "avoid unpaved roads car"
        m_names[TBoolSettings.ERecordAudio.value] = "recording audio"

        // int settings

        m_names[TIntSettings.EMapViewAngle.value] = "map view angle"
        m_names[TIntSettings.EZoomLevel.value] = "zoom level"
        m_names[TIntSettings.ETravelModeCar.value] = "travel mode car"
        m_names[TIntSettings.EDemoSpeed.value] = "demo speed"
        m_names[TIntSettings.EUnitsSystem.value] = "units system"
        m_names[TIntSettings.ERecordingChunk.value] = "recording chunk"
        m_names[TIntSettings.EMinMinutes.value] = "min minutes to keep"
        m_names[TIntSettings.EDiskLimit.value] = "recording disk limit"

        // double settings

        m_names[TDoubleSettings.ELatitude.value] = "latitude"
        m_names[TDoubleSettings.ELongitude.value] = "longitude"
        m_names[TDoubleSettings.EMapStyle.value] = "map style"

        // string settings

        m_names[TStringSettings.EVoice.value] = "voice"
    }

    private fun setDefaultValues() {
        // bool settings

        m_boolDefaultValues[TBoolSettings.EUseMobileData.value] = true
        m_boolDefaultValues[TBoolSettings.EUseMobileDataForTraffic.value] = true
        m_boolDefaultValues[TBoolSettings.EUseMobileDataForMapAndWikipedia.value] = true
        m_boolDefaultValues[TBoolSettings.EUseMobileDataForTerrainAndSatellite.value] = false
        m_boolDefaultValues[TBoolSettings.EAvoidMotorwaysCar.value] = false
        m_boolDefaultValues[TBoolSettings.EAvoidTollRoadsCar.value] = false
        m_boolDefaultValues[TBoolSettings.EAvoidFerriesCar.value] = false
        m_boolDefaultValues[TBoolSettings.EAvoidUnpavedRoadsCar.value] = true

        // int settings

        m_intDefaultValues[TIntSettings.EMapViewAngle.value - TIntSettings.EIntSettingsBase.value] =
            0
        m_intDefaultValues[TIntSettings.EZoomLevel.value - TIntSettings.EIntSettingsBase.value] = 80
        m_intDefaultValues[TIntSettings.ETravelModeCar.value - TIntSettings.EIntSettingsBase.value] =
            ERouteType.ERT_Fastest.value
        m_intDefaultValues[TIntSettings.EDemoSpeed.value - TIntSettings.EIntSettingsBase.value] = 1
        m_intDefaultValues[TIntSettings.EUnitsSystem.value - TIntSettings.EIntSettingsBase.value] =
            EUnitSystem.EMetric.value

        // double settings

        m_doubleDefaultValues[TDoubleSettings.ELatitude.value - TDoubleSettings.EDoubleSettingsBase.value] =
            Double.MAX_VALUE
        m_doubleDefaultValues[TDoubleSettings.ELongitude.value - TDoubleSettings.EDoubleSettingsBase.value] =
            Double.MAX_VALUE
        m_doubleDefaultValues[TDoubleSettings.EMapStyle.value - TDoubleSettings.EDoubleSettingsBase.value] =
            0.0

        // string settings

        m_stringDefaultValues[TStringSettings.EVoice.value - TStringSettings.EStringSettingsBase.value] =
            ""
        m_stringDefaultValues[TStringSettings.EAddressSearchCountryISOCode.value - TStringSettings.EStringSettingsBase.value] =
            ""

        // Recorder

        m_intDefaultValues[TIntSettings.ERecordingChunk.value] = 5 //5 min
        m_boolDefaultValues[TBoolSettings.ERecordAudio.value] = false
        m_intDefaultValues[TIntSettings.EMinMinutes.value] = 30 //30 min
        m_intDefaultValues[TIntSettings.EDiskLimit.value] = 1000 //1gb
    }

    private fun setCallbacks() {
        GEMSdkCall.checkCurrentThread()
        // bool settings

        val useMobileData = { value: Boolean ->
            if (m_service != null) {
                if (value) {
                    val bUseMobileDataForTraffic =
                        getBooleanValue(TBoolSettings.EUseMobileDataForTraffic.value)
                    val bUseMobileDataForMapAndWikipedia =
                        getBooleanValue(TBoolSettings.EUseMobileDataForMapAndWikipedia.value)
                    val bUseMobileDataForTerrainAndSatellite =
                        getBooleanValue(TBoolSettings.EUseMobileDataForTerrainAndSatellite.value)

                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.ETrafficService, bUseMobileDataForTraffic.second
                    )
                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.EMapDataService, bUseMobileDataForMapAndWikipedia.second
                    )
                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.ETerrainService,
                        bUseMobileDataForTerrainAndSatellite.second
                    )
                } else {
                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.ETrafficService, value
                    )
                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.EMapDataService, value
                    )
                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.ETerrainService, value
                    )
                }
            }
        }
        m_boolCallbacks[TBoolSettings.EUseMobileData.value] = useMobileData

        val useMobileDataForTraffic = { value: Boolean ->
            if (m_service != null) {
                CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                    EServiceGroupType.ETrafficService, value
                )
            }
        }
        m_boolCallbacks[TBoolSettings.EUseMobileDataForTraffic.value] = useMobileDataForTraffic

        val useMobileDataForMapAndWikipedia = { value: Boolean ->
            if (m_service != null) {
                CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                    EServiceGroupType.EMapDataService,
                    value
                )
                if (value) {
                    val bUseMobileDataForTerrainAndSatellite =
                        getBooleanValue(TBoolSettings.EUseMobileDataForTerrainAndSatellite.value)

                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.ETerrainService,
                        bUseMobileDataForTerrainAndSatellite.second
                    )
                } else {
                    CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                        EServiceGroupType.ETerrainService, value
                    )
                }
            }
        }
        m_boolCallbacks[TBoolSettings.EUseMobileDataForMapAndWikipedia.value] =
            useMobileDataForMapAndWikipedia

        val useMobileDataForTerrainAndSatellite = { value: Boolean ->
            if (m_service != null) {
                CommonSettings().setAllowOffboardServiceOnExtraChargedNetwork(
                    EServiceGroupType.ETerrainService,
                    value
                )
            }
        }
        m_boolCallbacks[TBoolSettings.EUseMobileDataForTerrainAndSatellite.value] =
            useMobileDataForTerrainAndSatellite

        // int settings

        val unitsSystem = { value: Int ->
            if (m_service != null) {
                CommonSettings().setUnitSystem(EnumHelp.fromInt(value))
            }
        }
        m_intCallbacks[TIntSettings.EUnitsSystem.value - TIntSettings.EIntSettingsBase.value] =
            unitsSystem

    }


    fun loadRoutePreferences(): RoutePreferences {
        GEMSdkCall.checkCurrentThread()

        val routeTypeSetting = getIntValue(TIntSettings.ETravelModeCar.value)
        val avoidMotorwaysSetting = getBooleanValue(TBoolSettings.EAvoidMotorwaysCar.value)
        val avoidTollSetting = getBooleanValue(TBoolSettings.EAvoidTollRoadsCar.value)
        val avoidFerriesSetting = getBooleanValue(TBoolSettings.EAvoidFerriesCar.value)
        val avoidUnpavedSetting = getBooleanValue(TBoolSettings.EAvoidUnpavedRoadsCar.value)

        val preferences = RoutePreferences()
        preferences.setTransportMode(ERouteTransportMode.ETM_Car)
        preferences.setRouteType(EnumHelp.fromInt(routeTypeSetting.second))
        preferences.setAvoidTollRoads(avoidTollSetting.second)
        preferences.setAvoidMotorways(avoidMotorwaysSetting.second)
        preferences.setAvoidFerries(avoidFerriesSetting.second)
        preferences.setAvoidUnpavedRoads(avoidUnpavedSetting.second)

        return preferences
    }
}
