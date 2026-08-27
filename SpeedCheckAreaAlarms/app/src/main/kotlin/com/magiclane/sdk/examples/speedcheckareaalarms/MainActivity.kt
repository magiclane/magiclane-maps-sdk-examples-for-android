/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.speedcheckareaalarms

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
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
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
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
import com.magiclane.sdk.examples.speedcheckareaalarms.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.speedcheckareaalarms.databinding.DialogLayoutBinding
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
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {
    private companion object {
        // Distance ahead of the section entry gantry at which the approaching alarm fires.
        const val ALARM_DISTANCE_METERS = 800.0
        const val SPEED_SECTION_HIGHLIGHT_ID = 1

        // Window insets the map should keep clear so the Magic Lane logo stays visible.
        val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    /**
     * Everything the speed check area panel displays for the section currently
     * approached or traveled.
     */
    private data class SpeedSectionPanelState(
        val icon: Bitmap?,
        val distanceText: String,
        val averageSpeed: String,
        val averageSpeedUnit: String,
        val isAverageSpeedOverLimit: Boolean,
        val isInsideSection: Boolean,
    )

    private lateinit var binding: ActivityMainBinding
    private val mapView
        get() = binding.gemSurfaceView.mapView

    private val isLandscape
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private var alarmImageSize = 0

    // Uid of the highlighted section entry gantry, to avoid re-highlighting the same section.
    private var highlightedEntryId = 0L

    // Entry gantry icon, gathered once when the approaching alarm fires (as Magic Earth does).
    private var entryImage: Bitmap? = null
    private var entryImageId = 0L

    // Whether the position is currently inside a speed check area - guards the enter/ exit TTS.
    private var insideSpeedSection = false

    // Whether the panel currently shows an approaching (not yet entered) section. An
    // approaching section gets no travel notifications, so its distance is refreshed
    // on every navigation instruction update instead.
    private var approachingSpeedSection = false

    private var panelState: SpeedSectionPanelState? = null

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    // Define an alarm service to be able to track speed check areas along the route.
    private var alarmService: AlarmService? = null

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
                    playTextWarning(getString(R.string.tts_approaching_speed_check_area))
                }
                updateSpeedSectionInfo()
            }
        },

        onEnterSpeedSectionAlarms = {
            SdkCall.execute {
                val wasInside = insideSpeedSection
                insideSpeedSection = !alarmService?.speedSectionAlarms.isNullOrEmpty()
                if (insideSpeedSection && !wasInside) {
                    playTextWarning(getString(R.string.tts_entering_speed_check_area))
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
                    playTextWarning(getString(R.string.tts_exiting_speed_check_area))
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

        onNavigationInstructionUpdated = {
            // An approaching (not yet entered) section gets no travel notifications -
            // refresh its distance here.
            if (approachingSpeedSection) {
                SdkCall.execute {
                    updateSpeedSectionInfo()
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
        applySpeedSectionPanelWidth()

        // A 180-degree landscape flip moves the display cutout to the other side
        // without a configuration change - the insets then shift the panel, so track
        // its edges to keep the GPS arrow centered in the space left free.
        binding.speedSectionPanel.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
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
        applySpeedSectionPanelWidth()
        // Defer until the panel has been re-laid out for the new orientation.
        binding.root.doOnPreDraw { updateFollowPositionCameraFocus() }
    }

    override fun onDestroy() {
        clearSdkListeners()
        hideSpeedSectionPanel()
        alarmService = null
        SdkCall.execute { removeHighlightedSection() }

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
     * bar or a display cutout. When the speed check area panel is showing, the
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

            // Keep the logo above the speed check area panel when it is visible,
            // otherwise just above the bottom system inset.
            val bottom = if (binding.speedSectionPanel.isVisible) {
                binding.speedSectionPanel.top.coerceAtLeast(top)
            } else {
                (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            currentMapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    private fun configureGpsButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            mapView?.apply {
                onExitFollowingPosition = {
                    if (SdkCall.execute { navigationService.isSimulationActive() } == true) {
                        hideSpeedSectionPanel()
                        setFollowGpsButtonVisibility(isVisible = true)
                    }
                }

                onEnterFollowingPosition = {
                    panelState?.let { showSpeedSectionPanel(it) }
                    setFollowGpsButtonVisibility(isVisible = false)
                }

                // Set on click action for the GPS button.
                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    /**
     * Produces the alarm service and enables the section-control (average speed check
     * area) alarms. They are available only when the safety cameras overlay is added
     * to the alarm service overlay collection.
     */
    private fun setSpeedSectionAlarms() {
        SdkCall.execute {
            alarmService = AlarmService.produce(alarmListener)?.apply {
                alarmDistance = ALARM_DISTANCE_METERS
                OverlayService().getAvailableOverlays(null)?.first?.let { list ->
                    overlays?.add(ArrayList(list.filter { it.uid == ECommonOverlayId.Safety.value }))
                }
                speedSectionAlarmEnabled = true
            }
        }
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("A", 65.83851, -23.25851),
            Landmark("B", 65.78518, -23.19314),
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
     * Refreshes the speed check area panel, the entry/ exit gantry highlights and the
     * average speed from the current alarm service state. Must be called on the SDK thread.
     *
     * A section currently traveled takes precedence; otherwise an approaching section
     * is displayed; with neither, the panel is hidden and the highlights removed.
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
        entryImage = null
        entryImageId = 0L
        removeHighlightedSection()
        panelState = null
        Util.postOnMain { hideSpeedSectionPanel() }
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

        val state = SpeedSectionPanelState(
            icon = getEntryImage(alarm.entry),
            distanceText = getString(R.string.speed_section_out_in, "$distValue $distUnit"),
            averageSpeed = avgSpeed,
            averageSpeedUnit = avgSpeedUnit,
            isAverageSpeedOverLimit = isOverLimit,
            isInsideSection = true,
        )
        postSpeedSectionPanelUpdate(state)
    }

    private fun updateApproachingSpeedSectionInfo(alarm: SpeedSectionAlarm) {
        val route = navRoute ?: return
        val entryCoordinates = alarm.entry?.coordinates ?: return

        // Distance to the section entry gantry, measured along the navigation route.
        val distance = route.getDistanceOnRoute(entryCoordinates, true).coerceAtLeast(0)

        approachingSpeedSection = true

        // Highlight the entry and exit gantries on the map.
        highlightSection(alarm.entry, alarm.exit)

        val unitSystem = SdkSettings.unitSystem
        val (distValue, distUnit) = GemUtil.getDistText(distance, unitSystem, bHighResolution = true)

        // No average speed while approaching - the related fields stay empty.
        val state = SpeedSectionPanelState(
            icon = getEntryImage(alarm.entry),
            distanceText = getString(R.string.speed_section_in, "$distValue $distUnit"),
            averageSpeed = "",
            averageSpeedUnit = "",
            isAverageSpeedOverLimit = false,
            isInsideSection = false,
        )
        postSpeedSectionPanelUpdate(state)
    }

    /**
     * Stores the panel state and shows the panel only while the map follows the GPS
     * position. Must be called on the SDK thread. While the user browses the map the
     * state is just remembered - re-entering follow position mode restores the panel.
     */
    private fun postSpeedSectionPanelUpdate(state: SpeedSectionPanelState) {
        panelState = state
        if (mapView?.isFollowingPosition() == true) {
            Util.postOnMain { showSpeedSectionPanel(state) }
        }
    }

    /**
     * The entry gantry icon is gathered only once - at the approaching notification.
     */
    private fun getEntryImage(entry: OverlayItem?): Bitmap? {
        val image = entry?.image ?: return entryImage
        if (entryImage == null || entryImageId != entry.uid) {
            // Fit the icon inside the alarm image box keeping its native aspect ratio -
            // rendering it square would distort it.
            val aspectRatio = image.aspectRatio
            val width = ((aspectRatio?.width ?: 1f) * alarmImageSize).toInt().coerceAtLeast(1)
            val height = ((aspectRatio?.height ?: 1f) * alarmImageSize).toInt().coerceAtLeast(1)
            entryImage = GemUtilImages.asBitmap(image, width, height)
            entryImageId = entry.uid
        }
        return entryImage
    }

    /**
     * Highlights the entry and exit gantries of the given section - same look as for
     * an ordinary safety camera alarm. The highlight is refreshed only when a
     * different section is alarmed.
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

        mapView?.activateHighlightLandmarks(landmarks, displaySettings, SPEED_SECTION_HIGHLIGHT_ID)
        highlightedEntryId = entry.uid
    }

    private fun removeHighlightedSection() {
        highlightedEntryId = 0L
        mapView?.deactivateHighlight(SPEED_SECTION_HIGHLIGHT_ID)
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
            hideSpeedSectionPanel()
            setFollowGpsButtonVisibility(isVisible = false)
        }

        SdkCall.execute {
            removeHighlightedSection()
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

    private fun playTextWarning(text: String) {
        if (!SoundPlayingService.ttsPlayerIsInitialized) {
            return
        }

        SoundPlayingService.playText(
            text,
            SoundPlayingListener(),
            SoundPlayingPreferences(),
        )
    }

    /**
     * The panel spans the whole screen width in portrait and only the left half of
     * the screen in landscape, leaving the right half free for the GPS arrow.
     */
    private fun applySpeedSectionPanelWidth() {
        val params = binding.speedSectionPanel.layoutParams as ConstraintLayout.LayoutParams
        if (isLandscape) {
            params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT
            params.matchConstraintPercentWidth = 0.5f
        } else {
            // Spread width fills the space between the constraints, respecting the margins.
            params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
            params.matchConstraintPercentWidth = 1f
        }
        binding.speedSectionPanel.layoutParams = params
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
        val x = if (isLandscape && binding.speedSectionPanel.isVisible && screenWidth > 0) {
            (binding.speedSectionPanel.right + screenWidth) / (2f * screenWidth)
        } else {
            0.5f
        }
        SdkCall.execute {
            val preferences = mapView?.preferences?.followPositionPreferences ?: return@execute
            preferences.cameraFocus = XyF(x, preferences.cameraFocus.y)
        }
    }

    private fun showSpeedSectionPanel(state: SpeedSectionPanelState) {
        binding.apply {
            speedSectionPanel.visibility = View.VISIBLE
            state.icon?.let { speedSectionImage.setImageBitmap(it) }
            speedSectionDistance.text = state.distanceText

            averageSpeedGroup.visibility = if (state.isInsideSection) View.VISIBLE else View.GONE
            averageSpeedValue.text = state.averageSpeed
            averageSpeedUnit.text = state.averageSpeedUnit

            // The average speed turns red when it exceeds the enforced section limit.
            val speedColor = ContextCompat.getColor(
                this@MainActivity,
                if (state.isAverageSpeedOverLimit) R.color.over_limit else R.color.on_surface,
            )
            averageSpeedValue.setTextColor(speedColor)
            averageSpeedUnit.setTextColor(speedColor)
        }

        EspressoIdlingResource.updateAlarmVisibility(isVisible = true)
        // Defer until the panel has been laid out so its edges are known, then lift
        // the logo above it and center the GPS arrow in the space it leaves free.
        binding.speedSectionPanel.post {
            updateFollowPositionCameraFocus()
            updateFocusViewport()
        }
    }

    private fun hideSpeedSectionPanel() {
        binding.speedSectionPanel.visibility = View.GONE
        EspressoIdlingResource.updateAlarmVisibility(isVisible = false)
        updateFollowPositionCameraFocus()
        // Restore the logo to its default bottom position.
        updateFocusViewport()
    }

    private fun setFollowGpsButtonVisibility(isVisible: Boolean) {
        binding.followGpsButton.visibility = if (isVisible) View.VISIBLE else View.GONE
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
    private const val RESOURCE_NAME = "SpeedCheckAreaAlarmsIdlingResource"
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
