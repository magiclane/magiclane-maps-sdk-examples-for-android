/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.displaycurrentstreetinfocompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.navigation.CurrentStreetPanelData
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.sensordatasource.ImprovedPositionData
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

/**
 * Owns the improved-position listener and turns each update into the data of the
 * maps-compose library current-street panel ([CurrentStreetPanelData]) plus the road
 * speed limit shown in the round sign.
 */
class DisplayCurrentStreetInfoModel(application: Application) : AndroidViewModel(application) {

    var errorMessage by mutableStateOf("")

    /** Startup explanation shown once the first valid position arrives. */
    var infoMessage by mutableStateOf("")

    /** Data of the library current-street panel; empty until the first position update. */
    var currentStreetData by mutableStateOf(CurrentStreetPanelData())
        private set

    /** Road speed limit in km/h, or empty while unknown. */
    var speedLimitText by mutableStateOf("")
        private set

    // Guards against double-registration: startPositionTracking() is called from both
    // the map-data-ready effect and the permission-granted callback.
    private var positionListenerAdded = false

    private var mapState: GemMapState? = null
    private var isFollowingStarted = false
    private var startupInfoShown = false

    private val positionListener = PositionListener { position -> onNewPosition(position) }

    /** Starts listening for improved position updates (call once location permission is granted). */
    fun startPositionTracking(mapState: GemMapState) {
        this.mapState = mapState
        SdkCall.execute {
            if (!positionListenerAdded) {
                positionListenerAdded = true
                PositionService.addListener(positionListener, EDataType.ImprovedPosition)
            }
        }
    }

    // Keeps the followed-position arrow clear of the bottom-centered street panel: the
    // landscape map is short, so at the default focus point the arrow sits behind the
    // panel. Portrait keeps the usual lower-half placement.
    fun applyCameraFocus(mapState: GemMapState, isLandscape: Boolean) {
        mapState.postToMap { mapView ->
            mapView.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.5f, 0.6f) else XyF(0.5f, 0.75f)
        }
    }

    override fun onCleared() {
        SdkCall.execute { PositionService.removeListener(positionListener) }
        super.onCleared()
    }

    // Runs on the SDK listener thread.
    private fun onNewPosition(position: PositionData) {
        if (position.isValid()) {
            // Start following the position on the first valid fix.
            if (!isFollowingStarted) {
                mapState?.takeIf { it.isMapReady }?.let { map ->
                    isFollowingStarted = true
                    map.startFollowingPosition()
                }
            }
            if (!startupInfoShown) {
                startupInfoShown = true
                Util.postOnMain {
                    infoMessage = getApplication<Application>().getString(R.string.startup_info_message)
                }
            }
        }

        val improvedPosition = ImprovedPositionData(position)
        val speedLimitKmh = (improvedPosition.roadSpeedLimit * 3.6).toInt()
        val roadAddress = improvedPosition.roadAddress
        val streetName = roadAddress?.getField(EAddressField.StreetName) ?: ""
        var cityName = roadAddress?.getField(EAddressField.City) ?: ""

        // If the GPS fix carries no city, query the nearest address for a fallback name.
        if (streetName.isNotEmpty() && cityName.isEmpty()) {
            mapState?.mapView?.getClosestAddress(improvedPosition.coordinates, CLOSEST_ADDRESS_RANGE_M, true)
                ?.addressInfo?.getField(EAddressField.City)?.let { city ->
                    cityName = getApplication<Application>().getString(R.string.near_city, city)
                }
        }

        val streetData = CurrentStreetPanelData(streetName = streetName, cityName = cityName)
        val limitText = if (speedLimitKmh > 0) "$speedLimitKmh" else ""
        Util.postOnMain {
            currentStreetData = streetData
            speedLimitText = limitText
        }
    }

    private companion object {
        const val CLOSEST_ADDRESS_RANGE_M = 10000
    }
}
