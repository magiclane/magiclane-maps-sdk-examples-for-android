/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routealarms

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
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

        // Window insets the map should keep clear so the Magic Lane logo stays visible.
        val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding
    private val mapView
        get() = binding.gemSurfaceView.mapView

    private val isLandscape
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

        onDestinationReached = { onNavigationEnded() },

        // Notify the user if navigation stops because of an error.
        onNavigationError = { error -> onNavigationEnded(error) },
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            // Surface any routing failure to the user.
            if (errorCode != GemError.NoError) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: ""
                showDialog(getString(R.string.routing_error, message))
            }
        },

        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SoundUtils.addTTSPlayerInitializationListener(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        alarmImageSize = resources.getDimensionPixelSize(R.dimen.alarm_image_size)
        applyAlarmPanelWidth()

        // A 180-degree landscape flip moves the display cutout to the other side
        // without a configuration change - the insets then shift the panel, so track
        // its edges to keep the GPS arrow centered in the space left free.
        binding.alarmPanel.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (left != oldLeft || right != oldRight) {
                updateFollowPositionCameraFocus()
            }
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    // The activity handles orientation changes itself (configChanges in the manifest),
    // so the panel width and the follow position camera focus are refreshed here.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyAlarmPanelWidth()
        // Defer until the panel has been re-laid out for the new orientation.
        binding.root.doOnPreDraw { updateFollowPositionCameraFocus() }
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
            // onSdkInitFailed already runs on the SDK thread, so getMessage needs no SdkCall wrapper.
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            // Align the Magic Lane logo with the system window insets once the map exists.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }

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
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
    }

    /**
     * Adjusts the map's focus viewport so the Magic Lane logo (anchored to the
     * bottom-left of the viewport) is not hidden behind the status bar, navigation
     * bar or a display cutout. When the speed-camera alarm panel is showing, the
     * logo is lifted above it as well.
     */
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val currentMapView = mapView ?: return@runSynced
            val viewport = currentMapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val left = insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)

            // Keep the logo above the alarm panel when it is visible, otherwise just
            // above the bottom system inset.
            val bottom = if (binding.alarmPanel.isVisible) {
                binding.alarmPanel.top.coerceAtLeast(top)
            } else {
                (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            currentMapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    /**
     * The panel spans the whole screen width in portrait and only the left half of
     * the screen in landscape, leaving the right half free for the GPS arrow.
     */
    private fun applyAlarmPanelWidth() {
        val params = binding.alarmPanel.layoutParams as ConstraintLayout.LayoutParams
        if (isLandscape) {
            params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT
            params.matchConstraintPercentWidth = 0.5f
        } else {
            // Spread width fills the space between the constraints, respecting the margins.
            params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
            params.matchConstraintPercentWidth = 1f
        }
        binding.alarmPanel.layoutParams = params
    }

    /**
     * Places the follow position camera focus (the GPS arrow) horizontally at the
     * center of the map area not covered by the panel: while the panel covers the
     * left half of a landscape screen, the center of the space remaining at its
     * right (computed from the laid out panel, which a display cutout may have
     * shifted right); 0.5 otherwise. Must be called after the panel is laid out.
     */
    private fun updateFollowPositionCameraFocus() {
        val screenWidth = binding.root.width
        val x = if (isLandscape && binding.alarmPanel.isVisible && screenWidth > 0) {
            (binding.alarmPanel.right + screenWidth) / (2f * screenWidth)
        } else {
            0.5f
        }
        SdkCall.execute {
            val preferences = mapView?.preferences?.followPositionPreferences ?: return@execute
            preferences.cameraFocus = XyF(x, preferences.cameraFocus.y)
        }
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

        val error = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (error != GemError.NoError) {
            // We are already on the SDK thread here, so getMessage needs no SdkCall wrapper.
            val message = GemError.getMessage(error, this@MainActivity)
            Util.postOnMain {
                showDialog(getString(R.string.route_simulation_error, message))
            }
        }
    }

    /**
     * Cleans up the map when navigation ends, either because the destination was
     * reached or because an error occurred. A non-fatal error is reported to the user.
     */
    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: ""
                showDialog(message)
            }
            setFollowGpsButtonVisibility(isVisible = false)
        }

        SdkCall.execute {
            mapView?.preferences?.routes?.clear()
        }
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
        // Defer until the panel has been laid out so its edges are known, then lift
        // the logo above it and center the GPS arrow in the space it leaves free.
        binding.alarmPanel.post {
            updateFollowPositionCameraFocus()
            updateFocusViewport()
        }
    }

    private fun hideAlarmPanel() {
        binding.alarmPanel.visibility = View.GONE
        EspressoIdlingResource.updateAlarmVisibility(isVisible = false)
        updateFollowPositionCameraFocus()
        // Restore the logo to its default bottom position.
        updateFocusViewport()
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
