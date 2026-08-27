/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.socialeventvotingcompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.social.EventVotingPanelData
import com.magiclane.sdk.compose.format.ValueWithUnit
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.ESocialOverlayParamsKeys
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SocialOverlay
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapView
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
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Owns the navigation simulation and the alarm service feeding the maps-compose
 * library event voting panel (via [EventVotingPanelData]): tracks the social reports
 * (police, speed cameras, accidents, …) along the route, highlights the alarmed
 * report on the map, displays its score, report time and live distance, plays a TTS
 * warning when a report is approached, and submits the user's confirm vote.
 */
class SocialEventVotingModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    val navigationService = NavigationService()

    var errorMessage by mutableStateOf("")
    var progressBarIsVisible by mutableStateOf(false)

    /** True from simulation start until destination reached, error or stop. */
    var isNavigating by mutableStateOf(false)
        private set

    /** Data of the library voting panel; empty while no social report alarm is active. */
    var panelData by mutableStateOf(EventVotingPanelData())
        private set

    /**
     * True while the passed-over vote countdown runs: the alarmed report is behind the
     * position but the vote is still accepted for a few seconds. Drives the panel's
     * countdown bar; the panel is dismissed when the countdown finishes.
     */
    var isCountdownRunning by mutableStateOf(false)
        private set

    /** Set by MainActivity once the TTS engine is ready; guards playback attempts. */
    @Volatile
    var isTtsEngineInitialized = false

    private var mapState: GemMapState? = null

    // Pixel size of the box the alarmed report's icon is rendered into.
    private var eventIconSizePx = 0

    // Define an alarm service to be able to track the social reports along the route.
    private var alarmService: AlarmService? = null

    // The report currently displayed by the voting panel — kept to submit the vote.
    private var currentItem: OverlayItem? = null

    // Uid of the currently alarmed report, to detect new ones and avoid re-triggering TTS.
    private var alarmedItemUid = 0L

    private val soundPlayingListener = SoundPlayingListener()
    private val soundPlayingPreferences = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define an alarm listener that will receive the social report notifications from
     * the alarm service.
     * For more available methods you should check the documentation at
     * https://magiclane.com/documentation/
     */
    private val alarmListener = AlarmListener.create(
        onOverlayItemAlarmsUpdated = {
            SdkCall.execute execute@{
                val alarmsList = alarmService?.overlayItemAlarms ?: return@execute
                if (alarmsList.size == 0) return@execute

                val alarm = alarmsList.getItem(0) ?: return@execute
                val distance = distanceText(alarmsList.getDistance(0))

                if (alarmedItemUid != alarm.uid) {
                    // First time this report is alarmed — play TTS and open the voting panel.
                    alarmedItemUid = alarm.uid
                    currentItem = alarm
                    showVotingPanel(alarm, distance)
                } else {
                    // Same report — only refresh the live distance.
                    Util.postOnMain {
                        if (panelData.title.isNotEmpty()) {
                            panelData = panelData.copy(distance = distance)
                        }
                    }
                }
            }
        },

        onOverlayItemAlarmsPassedOver = {
            alarmedItemUid = 0L
            SdkCall.execute {
                // The vote is still accepted for a few seconds after passing the event —
                // keep a votable panel up with a countdown; otherwise dismiss right away.
                val votingEnabled = currentItem?.getPreviewData()
                    ?.find { it.key == ALLOW_THUMB_KEY }
                    ?.valueBoolean == true
                Util.postOnMain {
                    if (votingEnabled && panelData.title.isNotEmpty()) {
                        panelData = panelData.copy(distance = null)
                        isCountdownRunning = true
                    } else {
                        hidePanel()
                    }
                }
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
                setSocialReportAlarms()
                mapState?.mapView?.let { mapView ->
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }
                    mapView.followPosition()
                }
            }
            isNavigating = true
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
    fun startSimulation(mapState: GemMapState, eventIconSizePx: Int) {
        this.mapState = mapState
        this.eventIconSizePx = eventIconSizePx

        SdkCall.execute {
            if (navigationService.isSimulationActive(navigationListener)) return@execute

            val waypoints = arrayListOf(
                Landmark("A", 48.21611, 11.48100),
                Landmark("B", 48.22646, 11.46622),
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

    /** Applies the example's custom map style shipped in the app assets. */
    fun applyMapStyle(mapView: MapView) {
        val data = app.assets.open("Basic.style").use { it.readBytes() }
        if (data.isEmpty()) return
        SdkCall.execute {
            mapView.preferences?.setMapStyleByDataBuffer(DataBuffer(data))
        }
    }

    /**
     * Places the follow position camera focus (the GPS arrow) horizontally at [x]
     * (relative 0..1), in the lower part of the screen.
     */
    fun applyCameraFocus(mapState: GemMapState, x: Float) {
        mapState.postToMap { mapView ->
            mapView.preferences?.followPositionPreferences?.cameraFocus = XyF(x, CAMERA_FOCUS_Y)
        }
    }

    /**
     * Confirms the alarmed report on the user's thumb-up vote and dismisses the panel.
     */
    fun confirmVote() {
        val item = currentItem ?: run {
            hidePanel()
            return
        }
        val errorCode = SdkCall.execute {
            SocialOverlay.confirmReport(item, ProgressListener())
        } ?: GemError.General
        if (errorCode < GemError.NoError) {
            errorMessage = app.getString(
                R.string.confirm_report_failed,
                SdkCall.runSynced { GemError.getMessage(errorCode, app) },
            )
        }
        hidePanel()
    }

    /**
     * Dismisses the panel without denying the report: deny voting is intentionally
     * skipped in simulation — it should only happen when the user can physically
     * confirm the event is absent in the real world.
     */
    fun denyVote() {
        hidePanel()
    }

    /** Hides the voting panel and drops the alarmed report's map highlight. */
    fun hidePanel() {
        isCountdownRunning = false
        panelData = EventVotingPanelData()
        SdkCall.execute {
            currentItem = null
            removeEventHighlight()
        }
    }

    override fun onCleared() {
        SdkCall.execute {
            removeEventHighlight()
            alarmService = null
        }
        super.onCleared()
    }

    /**
     * Produces the alarm service and enables the social report alarms — available when
     * the social reports overlay is added to the alarm service overlay collection.
     * Must be called on the SDK thread.
     */
    private fun setSocialReportAlarms() {
        alarmService = AlarmService.produce(alarmListener)?.apply {
            alarmDistance = ALARM_DISTANCE_METERS
            OverlayService().getAvailableOverlays(null)?.first?.let { list ->
                overlays?.add(ArrayList(list.filter { it.uid == ECommonOverlayId.SocialReports.value }))
            }
        }
    }

    /**
     * Fills the voting panel from the alarmed report's preview data, plays the TTS
     * warning and highlights the report on the map. Must be called on the SDK thread.
     */
    private fun showVotingPanel(alarm: OverlayItem, distance: ValueWithUnit) {
        val previewData = alarm.getPreviewData()

        val categoryTts = previewData
            ?.find { it.key == ESocialOverlayParamsKeys.ReportCategNameTTS.value }
            ?.valueString ?: ""
        playTextWarning(app.getString(R.string.tts_caution_alarm, categoryTts))

        highlightEvent(alarm)

        val timestampUtcSeconds = previewData
            ?.find { it.key == ESocialOverlayParamsKeys.ReportCreateTimeUTC.value }
            ?.valueLong ?: 0
        val data = EventVotingPanelData(
            icon = getEventIcon(alarm),
            title = alarm.name.toString(),
            timestamp = formatEventTimestamp(timestampUtcSeconds),
            score = previewData
                ?.find { it.key == ESocialOverlayParamsKeys.ReportScore.value }
                ?.valueString.orEmpty(),
            distance = distance,
            isVotingEnabled = previewData?.find { it.key == ALLOW_THUMB_KEY }?.valueBoolean == true,
        )
        Util.postOnMain {
            isCountdownRunning = false
            panelData = data
        }
    }

    /** Must be called on the SDK thread. */
    private fun distanceText(distanceMeters: Float): ValueWithUnit {
        val (value, unit) = GemUtil.getDistText(
            distanceMeters.toInt().coerceAtLeast(0),
            SdkSettings.unitSystem,
            bHighResolution = true,
        )
        return ValueWithUnit(value, unit)
    }

    /** The alarmed report's own icon, rendered into the panel's icon box. */
    private fun getEventIcon(alarm: OverlayItem): ImageBitmap? =
        alarm.image?.asBitmap(eventIconSizePx, eventIconSizePx)?.asImageBitmap()

    // Returns "HH:mm" if the event occurred today, or "dd/MM/yyyy" for any earlier date.
    private fun formatEventTimestamp(stampUtcSeconds: Long): String {
        val eventTime = Calendar.getInstance(Locale.getDefault()).also {
            it.timeInMillis = stampUtcSeconds * 1000
        }
        val now = Calendar.getInstance(Locale.getDefault())
        val sameDay = eventTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            eventTime.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
            eventTime.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
        val pattern = if (sameDay) "HH:mm" else "dd/MM/yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(eventTime.timeInMillis))
    }

    /**
     * Highlights the alarmed report on the map using its own icon, enlarged so the
     * event the voting panel refers to is easy to spot. Must be called on the SDK thread.
     */
    private fun highlightEvent(alarm: OverlayItem) {
        val mapView = mapState?.mapView ?: return
        val image = alarm.image ?: return
        val coordinates = alarm.coordinates ?: return

        val landmark = Landmark().apply {
            this.image = image
            // Deep-copy: the overlay item coordinates are native-backed views.
            this.coordinates = Coordinates(coordinates.latitude, coordinates.longitude)
        }
        val landmarkList = LandmarkList().apply { add(landmark) }

        val highlightSettings = HighlightRenderSettings(
            EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
        ).also {
            // Enlarge the alarm icon so the highlighted event stands out on the map.
            it.imageSize = HIGHLIGHT_IMAGE_SIZE_MM
        }

        // Re-activating with the same id replaces any previously highlighted event.
        mapView.activateHighlightLandmarks(landmarkList, highlightSettings, EVENT_HIGHLIGHT_ID)
    }

    /** Must be called on the SDK thread. */
    private fun removeEventHighlight() {
        mapState?.mapView?.deactivateHighlight(EVENT_HIGHLIGHT_ID)
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
        Util.postOnMain { hidePanel() }

        SdkCall.execute {
            alarmedItemUid = 0L
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
        // Distance ahead of the report at which the alarm fires.
        const val ALARM_DISTANCE_METERS = 500.0

        // Id used to track the alarmed event highlight so it can be removed individually.
        const val EVENT_HIGHLIGHT_ID = 0

        // Size of the highlighted event icon on the map, in mm.
        const val HIGHLIGHT_IMAGE_SIZE_MM = 10.0

        // Vertical follow-position camera focus: the GPS arrow sits in the lower quarter.
        const val CAMERA_FOCUS_Y = 0.75f

        // Preview-data key flagging that the report accepts thumb votes.
        const val ALLOW_THUMB_KEY = "allow_thumb"
    }
}
