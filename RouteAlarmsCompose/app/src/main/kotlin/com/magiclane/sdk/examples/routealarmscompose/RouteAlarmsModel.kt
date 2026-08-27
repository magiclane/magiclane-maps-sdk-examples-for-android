/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routealarmscompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.alarm.AlarmPanelData
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MarkerRenderSettings
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
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

/**
 * Owns the navigation simulation and the alarm service feeding the maps-compose
 * library alarm panel (via [AlarmPanelData]): tracks the safety cameras along the
 * route, highlights the alarmed camera and its field of view on the map and plays
 * a TTS warning when a camera is approached.
 */
class RouteAlarmsModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    val navigationService = NavigationService()

    var errorMessage by mutableStateOf("")
    var progressBarIsVisible by mutableStateOf(false)

    /** True from simulation start until destination reached, error or stop. */
    var isNavigating by mutableStateOf(false)
        private set

    /** Data of the library alarm panel; empty while no safety camera alarm is active. */
    var alarmPanelData by mutableStateOf(AlarmPanelData())
        private set

    /** Set by MainActivity once the TTS engine is ready; guards playback attempts. */
    @Volatile
    var isTtsEngineInitialized = false

    private var mapState: GemMapState? = null

    // Pixel size of the box the alarmed camera's icon is rendered into.
    private var alarmIconSizePx = 0

    // Define an alarm service to be able to track alarms on the map.
    private var alarmService: AlarmService? = null

    // Overlay uid of the alarmed safety camera; 0 while no alarm is active.
    private var safetyAlarmId = 0

    // The alarmed camera's icon, rendered once when its alarm becomes active.
    private var alarmIcon: ImageBitmap? = null

    private val soundPlayingListener = SoundPlayingListener()
    private val soundPlayingPreferences = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define an alarm listener that will receive notifications from the alarm
     * service. We will use just the overlay item methods, but for more available
     * methods you should check the documentation at https://magiclane.com/documentation/
     */
    private val alarmListener = AlarmListener.create(
        onOverlayItemAlarmsUpdated = {
            SdkCall.execute execute@{
                val service = alarmService ?: return@execute

                // Get the overlay items that are present and relevant.
                val alarmsList = service.overlayItemAlarms ?: return@execute
                if (alarmsList.size == 0) {
                    return@execute
                }

                // Get the distance to the closest alarm item; consider it only when
                // closer than the maximum distance until an alarm is reached.
                val distance = alarmsList.getDistance(0)
                if (distance > service.alarmDistance) {
                    return@execute
                }

                val alarm = alarmsList.getItem(0) ?: return@execute
                if (alarm.overlayUid != safetyAlarmId) {
                    if (safetyAlarmId != 0) {
                        removeHighlightedAlarm()
                    }

                    safetyAlarmId = alarm.overlayUid

                    alarmIcon = alarm.image?.let { image ->
                        alarm.coordinates?.let { coordinates ->
                            highlightAlarm(image, coordinates)
                        }
                        GemUtilImages.asBitmap(image, alarmIconSizePx, alarmIconSizePx)
                            ?.asImageBitmap()
                    }

                    playAlarmWarning()

                    // Paint the camera's field of view on the map.
                    mapState?.mapView?.displayOverlayItemFieldOfView(
                        createAlarmMarkerRenderSettings(),
                        alarm,
                        SAFETY_FIELD_OF_VIEW_TAG,
                    )
                }

                // If you are close enough to the alarm item, notify the user.
                val (distValue, distUnit) =
                    GemUtil.getDistText(distance.toInt(), SdkSettings.unitSystem, true)
                val data = AlarmPanelData(
                    icon = alarmIcon,
                    title = app.getString(R.string.speed_camera),
                    text = app.getString(R.string.alarm_distance, "$distValue $distUnit"),
                )
                Util.postOnMain { alarmPanelData = data }
            }
        },

        onOverlayItemAlarmsPassedOver = {
            Util.postOnMain { alarmPanelData = AlarmPanelData() }
            SdkCall.execute {
                removeHighlightedAlarm()
            }
        },
    )

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service. We will use just the onNavigationStarted method, but for
     * more available methods you should check the documentation.
     */
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                // Set the overlay for which to be notified.
                setAlarmOverlay(ECommonOverlayId.Safety)
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
                Landmark("A", 53.056306247688326, 8.882596560149098),
                Landmark("B", 53.06178963549359, 8.876610724727849),
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
            removeHighlightedAlarm()
            alarmService = null
        }
        super.onCleared()
    }

    /**
     * Produces the alarm service and requests notifications for the given overlay.
     * Must be called on the SDK thread.
     */
    private fun setAlarmOverlay(overlay: ECommonOverlayId) {
        alarmService = AlarmService.produce(alarmListener)?.apply {
            alarmDistance = ALARM_DISTANCE_METERS
            OverlayService().getAvailableOverlays(null)?.first?.let { list ->
                overlays?.add(ArrayList(list.filter { it.uid == overlay.value }))
            }
        }
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
            removeHighlightedAlarm()
            mapState?.mapView?.preferences?.routes?.clear()
        }
    }

    /**
     * Highlights the alarmed camera on the map - the same landmark look Magic Earth
     * uses for an alarmed safety camera. Must be called on the SDK thread.
     */
    private fun highlightAlarm(image: Image, coordinates: Coordinates) {
        val mapView = mapState?.mapView ?: return

        val landmark = Landmark().apply {
            this.image = image
            // Deep-copy: the overlay item coordinates are native-backed views.
            this.coordinates = Coordinates(coordinates.latitude, coordinates.longitude)
        }
        val landmarks = LandmarkList()
        landmarks.add(landmark)

        val displaySettings = HighlightRenderSettings(
            EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
        )

        mapView.activateHighlightLandmarks(landmarks, displaySettings, ALARM_HIGHLIGHT_ID)
    }

    /** Must be called on the SDK thread. */
    private fun removeHighlightedAlarm() {
        safetyAlarmId = 0
        alarmIcon = null
        mapState?.mapView?.let { mapView ->
            mapView.deactivateHighlight(ALARM_HIGHLIGHT_ID)
            mapView.hideCustomMarkers(SAFETY_FIELD_OF_VIEW_TAG)
        }
    }

    @Suppress("MagicNumber")
    private fun createAlarmMarkerRenderSettings() = MarkerRenderSettings().apply {
        polylineInnerColor = Rgba(243, 51, 243, 128)
        polylineOuterColor = Rgba(142, 36, 170, 128)
        polylineInnerSize = 0.0
        polylineOuterSize = 0.35
        polygonFillColor = Rgba(243, 51, 243, 128)
    }

    private fun playAlarmWarning() {
        if (!isTtsEngineInitialized || !SoundPlayingService.ttsPlayerIsInitialized) {
            return
        }

        SoundPlayingService.playText(
            app.getString(R.string.tts_speed_camera_warning),
            soundPlayingListener,
            soundPlayingPreferences,
        )
    }

    private companion object {
        // Distance ahead of a safety camera at which its alarm fires.
        const val ALARM_DISTANCE_METERS = 500.0
        const val ALARM_HIGHLIGHT_ID = 0
        const val SAFETY_FIELD_OF_VIEW_TAG = "safety_fov"
    }
}
