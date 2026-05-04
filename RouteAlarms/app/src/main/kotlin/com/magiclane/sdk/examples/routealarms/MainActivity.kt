/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routealarms

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MarkerRenderSettings
import com.magiclane.sdk.d3scene.OverlayService
import com.magiclane.sdk.examples.routealarms.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routealarms.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.routesandnavigation.AlarmListener
import com.magiclane.sdk.routesandnavigation.AlarmService
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {
    private companion object {
        const val ALARM_DISTANCE_METERS = 500.0
        const val ALARM_HIGHLIGHT_ID = 0
        const val ALARM_WARNING_TEXT = "Caution, Speed camera ahead"
        const val SAFETY_FIELD_OF_VIEW_TAG = "safety_fov"
    }

    private lateinit var binding: ActivityMainBinding
    private val mapView
        get() = binding.gemSurfaceView.mapView

    private var alarmImageSize = 0
    private var safetyAlarmId = 0

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    // Define an alarm service to be able to track alarms on the map.
    private var alarmService: AlarmService? = null

    /**
     * Define an alarm listener that will receive notifications from the
     * alarms service.
     * We will use just the onOverlayItemAlarmsUpdated method, but for more available
     * methods you should check the documentation at https://magiclane.com/documentation/
     */
    private val alarmListener = AlarmListener.create(
        onOverlayItemAlarmsUpdated = {
            SdkCall.execute execute@{
                // Get the overlay items that are present and relevant.
                val alarmsList = alarmService?.overlayItemAlarms ?: return@execute
                if (alarmsList.size == 0) {
                    return@execute
                }

                // Get the maximum distance until an alarm is reached.
                val maxDistance = alarmService?.alarmDistance ?: ALARM_DISTANCE_METERS

                // Get the distance to the closest alarm marker.
                val distance = alarmsList.getDistance(0)
                if (distance > maxDistance) {
                    return@execute
                }

                val alarm = alarmsList.getItem(0) ?: return@execute
                if (alarm.overlayUid != safetyAlarmId) {
                    if (safetyAlarmId != 0) {
                        removeHighlightedAlarm()
                    }

                    safetyAlarmId = alarm.overlayUid

                    val alarmBitmap = alarm.image?.let { image ->
                        alarm.coordinates?.let { coordinates ->
                            highlightAlarm(image, coordinates)
                        }
                        GemUtilImages.asBitmap(image, alarmImageSize, alarmImageSize)
                    }

                    playAlarmWarning()
                    mapView?.displayOverlayItemFieldOfView(
                        createAlarmMarkerRenderSettings(),
                        alarm,
                        SAFETY_FIELD_OF_VIEW_TAG,
                    )

                    Util.postOnMain {
                        showAlarmPanel(alarmBitmap)
                    }
                }

                // If you are close enough to the alarm item, notify the user.
                Util.postOnMain {
                    updateAlarmText(distance)
                }

                // Remove the alarm listener if you want to notify only once.
                // alarmService?.setAlarmListener(null)
            }
        },

        onOverlayItemAlarmsPassedOver = {
            Util.postOnMain {
                hideAlarmPanel()
            }
            SdkCall.execute {
                removeHighlightedAlarm()
            }
        },
    )

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     * We will use just the onNavigationStarted method, but for more available
     * methods you should check the documentation.
     */
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                // Set the overlay for which to be notified.
                setAlarmOverlay(ECommonOverlayId.Safety)
                mapView?.let { currentMapView ->
                    currentMapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        currentMapView.presentRoute(route)
                    }
                    configureGpsButton()
                    currentMapView.followPosition()
                }
            }
        },

        onDestinationReached = {
            SdkCall.execute {
                mapView?.let { currentMapView ->
                    currentMapView.preferences?.routes?.clear()
                }
            }

            setFollowGpsButtonVisibility(isVisible = false)
        },
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            binding.progressBar.visibility = View.GONE
        },

        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SoundUtils.addTTSPlayerInitializationListener(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        alarmImageSize = resources.getDimensionPixelSize(R.dimen.alarm_image_size)

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        hideAlarmPanel()
        alarmService = null
        removeHighlightedAlarm()

        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                startSimulation()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { _ -> }
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
    }

    private fun configureGpsButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            mapView?.apply {
                onExitFollowingPosition = {
                    if (SdkCall.execute { navigationService.isSimulationActive() } == true) {
                        hideAlarmPanel()
                        setFollowGpsButtonVisibility(isVisible = true)
                    }
                }

                onEnterFollowingPosition = {
                    if (safetyAlarmId != 0) {
                        showAlarmPanel()
                    }
                    setFollowGpsButtonVisibility(isVisible = false)
                }

                // Set on click action for the GPS button.
                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun setAlarmOverlay(overlay: ECommonOverlayId) {
        SdkCall.execute {
            alarmService = AlarmService.produce(alarmListener)
            alarmService?.alarmDistance = ALARM_DISTANCE_METERS
            OverlayService().getAvailableOverlays(null)?.first?.let { list ->
                alarmService?.overlays?.add(ArrayList(list.filter { it.uid == overlay.value }))
            }
        }
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("A", 53.056306247688326, 8.882596560149098),
            Landmark("B", 53.06178963549359, 8.876610724727849),
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun createAlarmMarkerRenderSettings() = MarkerRenderSettings().apply {
        polylineInnerColor = Rgba(243, 51, 243, 128)
        polylineOuterColor = Rgba(142, 36, 170, 128)
        polylineInnerSize = 0.0
        polylineOuterSize = 0.35
        polygonFillColor = Rgba(243, 51, 243, 128)
    }

    private fun playAlarmWarning() {
        if (!SoundPlayingService.ttsPlayerIsInitialized) {
            return
        }

        SoundPlayingService.playText(
            ALARM_WARNING_TEXT,
            SoundPlayingListener(),
            SoundPlayingPreferences(),
        )
    }

    private fun showAlarmPanel(alarmBitmap: Bitmap? = null) {
        binding.alarmPanel.visibility = View.VISIBLE
        alarmBitmap?.let { binding.alarmImage.setImageBitmap(it) }
        EspressoIdlingResource.updateAlarmVisibility(isVisible = true)
    }

    private fun hideAlarmPanel() {
        binding.alarmPanel.visibility = View.GONE
        EspressoIdlingResource.updateAlarmVisibility(isVisible = false)
    }

    private fun updateAlarmText(distance: Float) {
        binding.alarmText.text = getString(R.string.alarm_text, distance.toInt())
    }

    private fun setFollowGpsButtonVisibility(isVisible: Boolean) {
        binding.followGpsButton.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun highlightAlarm(image: Image, coordinates: Coordinates) {
        mapView?.let { currentMapView ->
            val landmark = Landmark()
            landmark.image = image
            landmark.coordinates = coordinates

            val lmkList = LandmarkList()
            lmkList.add(landmark)

            val displaySettings =
                HighlightRenderSettings(EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value)

            currentMapView.activateHighlightLandmarks(lmkList, displaySettings, ALARM_HIGHLIGHT_ID)
        }
    }

    private fun removeHighlightedAlarm() {
        safetyAlarmId = 0
        mapView?.let { currentMapView ->
            currentMapView.deactivateHighlight(ALARM_HIGHLIGHT_ID)
            currentMapView.hideCustomMarkers(SAFETY_FIELD_OF_VIEW_TAG)
        }
    }

    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }
}

//region TESTING
@VisibleForTesting
object EspressoIdlingResource {
    private const val RESOURCE_NAME = "RouteAlarmsIdlingResource"
    var alarmShows = false
        private set
    val espressoIdlingResource = CountingIdlingResource(RESOURCE_NAME)
    fun increment() = espressoIdlingResource.increment()

    fun decrement() {
        if (!espressoIdlingResource.isIdleNow) {
            espressoIdlingResource.decrement()
        }
    }

    fun updateAlarmVisibility(isVisible: Boolean) {
        if (alarmShows == isVisible) {
            return
        }

        alarmShows = isVisible
        if (isVisible) {
            increment()
        } else {
            decrement()
        }
    }
}
//endregion
