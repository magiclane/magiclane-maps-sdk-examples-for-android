/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.overspeedttswarningcompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.navigation.SpeedPanelData
import com.magiclane.sdk.compose.format.ValueWithUnit
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.sensordatasource.ImprovedPositionData
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.EStringIds
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

/**
 * Owns the position listener feeding the maps-compose library speed panel (via
 * [SpeedPanelData]) and plays a TTS warning while driving over the road speed limit.
 */
class OverspeedTTSWarningModel(application: Application) : AndroidViewModel(application) {

    var errorMessage by mutableStateOf("")

    /** Data of the library speed panel; empty until the first position update. */
    var speedPanelData by mutableStateOf(SpeedPanelData())
        private set

    /** Set by MainActivity once the TTS engine is ready; guards playback attempts. */
    @Volatile
    var isTtsEngineInitialized = false

    private var lastSpeedWarningTimeMs = 0L

    // Guards against double-registration: startPositionTracking() is called from both
    // the map-data-ready effect and the permission-granted callback.
    private var positionListenerAdded = false

    private var mapState: GemMapState? = null
    private var isFollowingStarted = false

    private val soundPlayingListener = object : SoundPlayingListener() {}
    private val soundPlayingPreferences = SoundPlayingPreferences()

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

    override fun onCleared() {
        SdkCall.execute { PositionService.removeListener(positionListener) }
        super.onCleared()
    }

    // Runs on the SDK listener thread.
    private fun onNewPosition(position: PositionData) {
        val speedLimit = ImprovedPositionData(position).roadSpeedLimit
        val isOverSpeeding = speedLimit > 0 && position.speed > speedLimit

        // Start following the position on the first valid fix.
        if (!isFollowingStarted && position.isValid()) {
            mapState?.takeIf { it.isMapReady }?.let { map ->
                isFollowingStarted = true
                map.startFollowingPosition()
            }
        }

        if (isOverSpeeding && isTtsEngineInitialized) {
            val now = System.currentTimeMillis()
            if (now - lastSpeedWarningTimeMs >= SPEED_WARNING_INTERVAL_MS) {
                lastSpeedWarningTimeMs = now
                SoundPlayingService.playText(
                    GemUtil.getTTSString(EStringIds.eStrMindYourSpeed),
                    soundPlayingListener,
                    soundPlayingPreferences,
                )
            }
        }

        val speedText = GemUtil.getSpeedText(position.speed, SdkSettings.unitSystem)
        val limitText = if (speedLimit > 0.0) {
            GemUtil.getSpeedText(speedLimit, SdkSettings.unitSystem)
        } else {
            null
        }

        val data = SpeedPanelData(
            currentSpeed = speedText.takeIf { it.first.isNotEmpty() }?.toValueWithUnit(),
            speedLimit = limitText?.toValueWithUnit(),
            isOverSpeeding = isOverSpeeding,
        )
        Util.postOnMain { speedPanelData = data }
    }

    private fun Pair<String, String>.toValueWithUnit() = ValueWithUnit(first, second)

    private companion object {
        const val SPEED_WARNING_INTERVAL_MS = 5 * 60 * 1000L
    }
}
