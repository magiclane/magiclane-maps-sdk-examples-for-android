/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.speedcheckareaalarmscompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.alarm.AlarmPanelData
import com.magiclane.sdk.compose.format.ValueWithUnit
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.OverlayItem
import com.magiclane.sdk.d3scene.OverlayService
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.routesandnavigation.AlarmListener
import com.magiclane.sdk.routesandnavigation.AlarmService
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.SpeedSectionAlarm
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

/**
 * Owns the navigation simulation and the alarm service feeding the maps-compose
 * library alarm panel (via [AlarmPanelData]): tracks the average speed check areas
 * (section control) along the route, highlights the section's entry and exit
 * gantries on the map, displays the distance to the section entry / exit and the
 * running average speed, and plays TTS messages when approaching, entering and
 * exiting a section.
 */
class SpeedCheckAreaAlarmsModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    val navigationService = NavigationService()

    var errorMessage by mutableStateOf("")
    var progressBarIsVisible by mutableStateOf(false)

    /** True from simulation start until destination reached, error or stop. */
    var isNavigating by mutableStateOf(false)
        private set

    /** Data of the library alarm panel; empty while no speed check area alarm is active. */
    var alarmPanelData by mutableStateOf(AlarmPanelData())
        private set

    /** Set by MainActivity once the TTS engine is ready; guards playback attempts. */
    @Volatile
    var isTtsEngineInitialized = false

    private var mapState: GemMapState? = null

    // Pixel size of the box the section entry gantry's icon is rendered into.
    private var alarmIconSizePx = 0

    // Define an alarm service to be able to track speed check areas along the route.
    private var alarmService: AlarmService? = null

    // Uid of the highlighted section entry gantry, to avoid re-highlighting the same section.
    private var highlightedEntryId = 0L

    // Entry gantry icon, gathered once when the approaching alarm fires (as Magic Earth does).
    private var entryIcon: ImageBitmap? = null
    private var entryIconId = 0L

    // Whether the position is currently inside a speed check area - guards the enter/ exit TTS.
    private var insideSpeedSection = false

    // Whether the panel currently shows an approaching (not yet entered) section. An
    // approaching section gets no travel notifications, so its distance is refreshed
    // on every navigation instruction update instead.
    private var approachingSpeedSection = false

    private val soundPlayingListener = SoundPlayingListener()
    private val soundPlayingPreferences = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define an alarm listener that will receive the section-control (average speed
     * check area) notifications from the alarm service.
     * For more available methods you should check the documentation at
     * https://magiclane.com/documentation/
     */
    private val alarmListener = AlarmListener.create(
        onApproachingSpeedSectionAlarms = {
            SdkCall.execute {
                // Announce only when not already traveling a speed check area.
                if (alarmService?.speedSectionAlarms.isNullOrEmpty()) {
                    playTextWarning(app.getString(R.string.tts_approaching_speed_check_area))
                }
                updateSpeedSectionInfo()
            }
        },

        onEnterSpeedSectionAlarms = {
            SdkCall.execute {
                val wasInside = insideSpeedSection
                insideSpeedSection = !alarmService?.speedSectionAlarms.isNullOrEmpty()
                if (insideSpeedSection && !wasInside) {
                    playTextWarning(app.getString(R.string.tts_entering_speed_check_area))
                }
                updateSpeedSectionInfo()
            }
        },

        onTravelSpeedSectionAlarms = {
            SdkCall.execute {
                updateSpeedSectionInfo()
            }
        },

        onExitSpeedSectionAlarms = {
            SdkCall.execute {
                val wasInside = insideSpeedSection
                insideSpeedSection = !alarmService?.speedSectionAlarms.isNullOrEmpty()
                if (wasInside && !insideSpeedSection) {
                    playTextWarning(app.getString(R.string.tts_exiting_speed_check_area))
                }
                updateSpeedSectionInfo()
            }
        },
    )

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                setSpeedSectionAlarms()
                mapState?.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }
                    mapView.followPosition()
                }
            }
            isNavigating = true
        },

        onNavigationInstructionUpdated = {
            // An approaching (not yet entered) section gets no travel notifications -
            // refresh its distance here.
            if (approachingSpeedSection) {
                SdkCall.execute {
                    updateSpeedSectionInfo()
                }
            }
        },

        onDestinationReached = { onNavigationEnded(GemError.NoError) },

        // Notify the user if navigation stops because of an error.
        onNavigationError = { error -> onNavigationEnded(error) },
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBarIsVisible = true
        },

        onCompleted = { errorCode, _ ->
            progressBarIsVisible = false

            // Surface any routing failure to the user.
            if (errorCode != GemError.NoError) {
                errorMessage = app.getString(
                    R.string.routing_error,
                    SdkCall.runSynced { GemError.getMessage(errorCode, app) },
                )
            }
        },

        postOnMain = true,
    )

    /** Starts the navigation simulation (call once the SDK map data is available). */
    fun startSimulation(mapState: GemMapState, alarmIconSizePx: Int) {
        this.mapState = mapState
        this.alarmIconSizePx = alarmIconSizePx

        SdkCall.execute {
            if (navigationService.isSimulationActive(navigationListener)) return@execute

            val waypoints = arrayListOf(
                Landmark("A", 65.83851, -23.25851),
                Landmark("B", 65.78518, -23.19314),
            )

            val error = navigationService.startSimulation(
                waypoints,
                navigationListener,
                routingProgressListener,
            )
            if (error != GemError.NoError) {
                // We are already on the SDK thread here, so getMessage needs no SdkCall wrapper.
                errorMessage = app.getString(
                    R.string.route_simulation_error,
                    GemError.getMessage(error, app),
                )
            }
        }
    }

    /**
     * Places the follow position camera focus (the GPS arrow) horizontally at [x]
     * (relative 0..1), preserving the vertical focus.
     */
    fun applyCameraFocus(mapState: GemMapState, x: Float) {
        mapState.postToMap { mapView ->
            val preferences = mapView.preferences?.followPositionPreferences ?: return@postToMap
            preferences.cameraFocus = XyF(x, preferences.cameraFocus.y)
        }
    }

    override fun onCleared() {
        SdkCall.execute {
            removeHighlightedSection()
            alarmService = null
        }
        super.onCleared()
    }

    /**
     * Produces the alarm service and enables the section-control (average speed check
     * area) alarms. They are available only when the safety cameras overlay is added
     * to the alarm service overlay collection. Must be called on the SDK thread.
     */
    private fun setSpeedSectionAlarms() {
        alarmService = AlarmService.produce(alarmListener)?.apply {
            alarmDistance = ALARM_DISTANCE_METERS
            OverlayService().getAvailableOverlays(null)?.first?.let { list ->
                overlays?.add(ArrayList(list.filter { it.uid == ECommonOverlayId.Safety.value }))
            }
            speedSectionAlarmEnabled = true
        }
    }

    /**
     * Refreshes the alarm panel data, the entry/ exit gantry highlights and the
     * average speed from the current alarm service state. Must be called on the SDK thread.
     *
     * A section currently traveled takes precedence; otherwise an approaching section
     * is displayed; with neither, the panel is emptied and the highlights removed.
     */
    private fun updateSpeedSectionInfo() {
        approachingSpeedSection = false

        val service = alarmService ?: return

        val activeAlarms = service.speedSectionAlarms
        if (!activeAlarms.isNullOrEmpty()) {
            // The list contains only the sections currently traveled - display the first one.
            updateTraveledSpeedSectionInfo(activeAlarms[0])
            return
        }

        // No section currently traveled - check for an approaching one.
        val approachingAlarms = service.approachingSpeedSectionAlarms
        if (!approachingAlarms.isNullOrEmpty()) {
            updateApproachingSpeedSectionInfo(approachingAlarms[0])
            return
        }

        // The alarm ended - drop the entry icon gathered at the approaching notification
        // and the highlighted entry/ exit gantries.
        entryIcon = null
        entryIconId = 0L
        removeHighlightedSection()
        Util.postOnMain { alarmPanelData = AlarmPanelData() }
    }

    private fun updateTraveledSpeedSectionInfo(alarm: SpeedSectionAlarm) {
        // Highlight the entry and exit gantries on the map.
        highlightSection(alarm.entry, alarm.exit)

        val unitSystem = SdkSettings.unitSystem
        val remainingDistance = alarm.remainingDistance.coerceAtLeast(0)
        val (distValue, distUnit) = GemUtil.getDistText(remainingDistance, unitSystem, bHighResolution = true)

        val (avgSpeed, avgSpeedUnit) = GemUtil.getSpeedText(alarm.speedAvg.toDouble(), unitSystem)
        val (limitSpeed, _) = GemUtil.getSpeedText(alarm.speedLimit.toDouble(), unitSystem)

        // Over the legal limit when the displayed (rounded) average exceeds the displayed limit.
        val isOverLimit = alarm.speedLimit > 0f &&
            (avgSpeed.toIntOrNull() ?: 0) > (limitSpeed.toIntOrNull() ?: Int.MAX_VALUE)

        val data = AlarmPanelData(
            icon = getEntryIcon(alarm.entry),
            title = app.getString(R.string.speed_check_area),
            text = app.getString(R.string.speed_section_out_in, "$distValue $distUnit"),
            metricLabel = app.getString(R.string.average_speed),
            metric = ValueWithUnit(avgSpeed, avgSpeedUnit),
            isMetricAlert = isOverLimit,
        )
        Util.postOnMain { alarmPanelData = data }
    }

    private fun updateApproachingSpeedSectionInfo(alarm: SpeedSectionAlarm) {
        val route = navRoute ?: return
        val entryCoordinates = alarm.entry?.coordinates ?: return

        // Distance to the section entry gantry, measured along the navigation route.
        val distance = route.getDistanceOnRoute(entryCoordinates, true).coerceAtLeast(0)

        approachingSpeedSection = true

        // Highlight the entry and exit gantries on the map.
        highlightSection(alarm.entry, alarm.exit)

        val (distValue, distUnit) =
            GemUtil.getDistText(distance, SdkSettings.unitSystem, bHighResolution = true)

        // No average speed while approaching - the panel's metric stays hidden.
        val data = AlarmPanelData(
            icon = getEntryIcon(alarm.entry),
            title = app.getString(R.string.speed_check_area),
            text = app.getString(R.string.speed_section_in, "$distValue $distUnit"),
        )
        Util.postOnMain { alarmPanelData = data }
    }

    /**
     * The entry gantry icon is gathered only once - at the approaching notification.
     */
    private fun getEntryIcon(entry: OverlayItem?): ImageBitmap? {
        val image = entry?.image ?: return entryIcon
        if (entryIcon == null || entryIconId != entry.uid) {
            // Fit the icon inside the alarm icon box keeping its native aspect ratio -
            // rendering it square would distort it.
            val aspectRatio = image.aspectRatio
            val width = ((aspectRatio?.width ?: 1f) * alarmIconSizePx).toInt().coerceAtLeast(1)
            val height = ((aspectRatio?.height ?: 1f) * alarmIconSizePx).toInt().coerceAtLeast(1)
            entryIcon = GemUtilImages.asBitmap(image, width, height)?.asImageBitmap()
            entryIconId = entry.uid
        }
        return entryIcon
    }

    /**
     * Highlights the entry and exit gantries of the given section - same look as for
     * an ordinary safety camera alarm. The highlight is refreshed only when a
     * different section is alarmed. Must be called on the SDK thread.
     */
    private fun highlightSection(entry: OverlayItem?, exit: OverlayItem?) {
        if (entry == null || entry.uid == highlightedEntryId) {
            return
        }
        removeHighlightedSection()

        val landmarks = LandmarkList()
        for (item in listOfNotNull(entry, exit)) {
            val itemImage = item.image ?: continue
            val itemCoordinates = item.coordinates ?: continue
            landmarks.add(
                Landmark().apply {
                    image = itemImage
                    // Deep-copy: the overlay item coordinates are native-backed views.
                    coordinates = Coordinates(itemCoordinates.latitude, itemCoordinates.longitude)
                },
            )
        }
        if (landmarks.size == 0) {
            return
        }

        val displaySettings =
            HighlightRenderSettings(EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value)

        mapState?.mapView?.activateHighlightLandmarks(landmarks, displaySettings, SPEED_SECTION_HIGHLIGHT_ID)
        highlightedEntryId = entry.uid
    }

    /** Must be called on the SDK thread. */
    private fun removeHighlightedSection() {
        highlightedEntryId = 0L
        mapState?.mapView?.deactivateHighlight(SPEED_SECTION_HIGHLIGHT_ID)
    }

    /**
     * Cleans up the map and the panel when navigation ends, either because the
     * destination was reached or because an error occurred. A non-fatal error is
     * reported to the user.
     */
    private fun onNavigationEnded(errorCode: Int) {
        if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
            errorMessage = app.getString(
                R.string.route_simulation_error,
                SdkCall.runSynced { GemError.getMessage(errorCode, app) },
            )
        }
        isNavigating = false
        Util.postOnMain { alarmPanelData = AlarmPanelData() }

        SdkCall.execute {
            entryIcon = null
            entryIconId = 0L
            insideSpeedSection = false
            approachingSpeedSection = false
            removeHighlightedSection()
            mapState?.mapView?.preferences?.routes?.clear()
        }
    }

    private fun playTextWarning(text: String) {
        if (!isTtsEngineInitialized || !SoundPlayingService.ttsPlayerIsInitialized) {
            return
        }

        SoundPlayingService.playText(
            text,
            soundPlayingListener,
            soundPlayingPreferences,
        )
    }

    private companion object {
        // Distance ahead of the section entry gantry at which the approaching alarm fires.
        const val ALARM_DISTANCE_METERS = 800.0
        const val SPEED_SECTION_HIGHLIGHT_ID = 1
    }
}
