/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.socialeventvoting

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.ESocialOverlayParamsKeys
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
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
import com.magiclane.sdk.examples.socialeventvoting.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.socialeventvoting.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.routesandnavigation.AlarmListener
import com.magiclane.sdk.routesandnavigation.AlarmService
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private companion object {
        const val ALARM_DISTANCE_METERS = 500.0

        // Id used to track the alarm landmark highlight so it can be removed individually.
        const val ALARM_HIGHLIGHT_ID = 0

        // System insets (status/navigation bars plus display cutout) used to keep the Magic Lane
        // logo and other map UI clear of system UI.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    private val navigationService = NavigationService()

    private var alarmService: AlarmService? = null

    // Tracks the currently displayed alarm to detect new ones and avoid re-triggering TTS.
    private var socialAlarmOverlayItem: OverlayItem? = null
    private var countdownTimer: CountDownTimer? = null

    private val alarmListener = AlarmListener.create(
        onOverlayItemAlarmsUpdated = {
            SdkCall.execute execute@{
                val alarmsList = alarmService?.overlayItemAlarms ?: return@execute
                if (alarmsList.size == 0) return@execute

                val alarm = alarmsList.getItem(0) ?: return@execute
                val distance = alarmsList.getDistance(0)

                if (socialAlarmOverlayItem?.overlayUid != alarm.overlayUid) {
                    // First time this alarm is encountered — play TTS and open the voting panel.
                    socialAlarmOverlayItem = alarm
                    val categoryTts = alarm.getPreviewData()
                        ?.find { it.key == ESocialOverlayParamsKeys.ReportCategNameTTS.value }
                        ?.valueString ?: ""
                    playAlarmWarning(getString(R.string.tts_caution_alarm, categoryTts))
                    Util.postOnMain { showVotingPanel(alarm, distance) }
                } else {
                    Util.postOnMain { updateAlarmDistance(distance) }
                }
            }
        },
        onOverlayItemAlarmsPassedOver = {
            // Capture and clear before the SDK call to avoid a race with onOverlayItemAlarmsUpdated.
            val item = socialAlarmOverlayItem
            socialAlarmOverlayItem = null
            SdkCall.execute {
                val votingEnabled = item?.getPreviewData()
                    ?.find { it.key == "allow_thumb" }
                    ?.valueBoolean == true
                Util.postOnMain { if (votingEnabled) startPassedCountdown() else hideVotingPanel() }
            }
        },
    )

    private val navigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                setAlarmOverlay(ECommonOverlayId.SocialReports)
                binding.gemSurfaceView.mapView?.let { mapView ->
                    navigationService.getNavigationRoute()?.let { route ->
                        mapView.presentRoute(route)
                    }
                    mapView.followPosition()
                }
            }
            applyCameraFocus()
            binding.progressBar.visibility = View.GONE
            enableGpsButton()
        },
        onDestinationReached = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.hideRoutes()
            }
            runOnAliveUi { disableGpsButton() }
        },
        onNavigationError = { error ->
            runOnAliveUi {
                disableGpsButton()
                showDialog(
                    getString(R.string.route_simulation_error, SdkCall.runSynced { GemError.getMessage(error, this) }),
                )
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SoundUtils.addTTSPlayerInitializationListener(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyOrientationLayout()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        countdownTimer = null
        alarmService = null

        clearSdkListeners()

        GemSdk.release()

        super.onDestroy()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            applyCustomAssetStyle(mapView)
            // Align the Magic Lane logo with the system window insets as soon as the map is created.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Delay simulation start until the worldwide road map is fully downloaded and up to date;
        // the callback is cleared immediately after firing to avoid repeat invocations.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                startSimulation()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    private fun enableGpsButton() {
        binding.gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = { binding.followGpsButton.visibility = View.VISIBLE }
            onEnterFollowingPosition = { binding.followGpsButton.visibility = View.GONE }
        }
        binding.followGpsButton.setOnClickListener {
            SdkCall.execute { binding.gemSurfaceView.mapView?.followPosition() }
        }
    }

    private fun disableGpsButton() {
        binding.gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {}
            onEnterFollowingPosition = {}
        }
        binding.followGpsButton.setOnClickListener(null)
        binding.followGpsButton.visibility = View.GONE
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("", 48.11005536802689, 11.520246863603928),
            Landmark("", 48.11376725816093, 11.517058814987786),
        )

        val error = navigationService.startSimulation(waypoints, navigationListener, ProgressListener())
        if (error != GemError.NoError) {
            runOnUiThread {
                showDialog(
                    getString(R.string.route_simulation_error, SdkCall.runSynced { GemError.getMessage(error, this) }),
                )
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

    private fun showVotingPanel(overlay: OverlayItem, alarmDistance: Float? = null) {
        countdownTimer?.cancel()
        countdownTimer = null
        binding.countdownProgress.visibility = View.GONE
        binding.eventVotingContainer.visibility = View.VISIBLE

        highlightAlarmOnMap(overlay)

        var bitmap: Bitmap? = null
        var nameText = ""
        var timeText = ""
        var scoreText = ""
        var showVoteButtons = false
        val eventImageSize = resources.getDimension(R.dimen.event_image_size).toInt()

        SdkCall.execute {
            val previewData = overlay.getPreviewData()
            bitmap = overlay.image?.asBitmap(eventImageSize, eventImageSize)
            nameText = overlay.name.toString()
            scoreText = previewData?.find { it.key == ESocialOverlayParamsKeys.ReportScore.value }?.valueString.toString()
            timeText = formatEventTimestamp(
                previewData?.find { it.key == ESocialOverlayParamsKeys.ReportCreateTimeUTC.value }?.valueLong ?: 0,
            )
            showVoteButtons = previewData?.find { it.key == "allow_thumb" }?.valueBoolean == true
        }

        binding.apply {
            icon.setImageBitmap(bitmap)
            text.text = nameText
            time.text = timeText
            score.text = scoreText
        }

        if (alarmDistance != null) {
            updateAlarmDistance(alarmDistance)
        } else {
            binding.distance.visibility = View.GONE
        }

        val buttonVisibility = if (showVoteButtons) View.VISIBLE else View.GONE
        binding.thumbUpButton.visibility = buttonVisibility
        binding.thumbDownButton.visibility = buttonVisibility

        if (showVoteButtons) {
            binding.thumbUpButton.setOnClickListener {
                val errorCode = SdkCall.execute { SocialOverlay.confirmReport(overlay, ProgressListener()) } ?: -1
                if (errorCode < 0) {
                    showDialog(
                        getString(
                            R.string.confirm_report_failed,
                            SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                        ),
                    )
                }
                hideVotingPanel()
            }
            binding.thumbDownButton.setOnClickListener {
                // Deny voting is intentionally skipped in simulation: it should only happen when
                // the user can physically confirm the event is absent in the real world.
                hideVotingPanel()
            }
        }
    }

    // Returns "HH:mm" if the event occurred today, or "dd/MM/yyyy" for any earlier date.
    private fun formatEventTimestamp(stampUtcSeconds: Long): String {
        val eventTime = Calendar.getInstance(Locale.getDefault()).also { it.timeInMillis = stampUtcSeconds * 1000 }
        val now = Calendar.getInstance(Locale.getDefault())
        val sameDay = eventTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            eventTime.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
            eventTime.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
        val pattern = if (sameDay) "HH:mm" else "dd/MM/yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(eventTime.timeInMillis))
    }

    private fun applyCustomAssetStyle(mapView: MapView) {
        val inputStream = applicationContext.resources.assets.open("Basic.style")
        val data = inputStream.readBytes()
        if (data.isEmpty()) return
        mapView.preferences?.setMapStyleByDataBuffer(DataBuffer(data))
    }

    @SuppressLint("SetTextI18n")
    private fun updateAlarmDistance(distance: Float) {
        val distancePair = GemUtil.getDistText(distance.toInt(), EUnitSystem.Metric, true)
        binding.distance.text = "${distancePair.first} ${distancePair.second}"
        binding.distance.visibility = View.VISIBLE
    }

    private fun startPassedCountdown() {
        binding.distance.visibility = View.INVISIBLE
        binding.countdownProgress.progress = 100
        binding.countdownProgress.visibility = View.VISIBLE

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(10_000L, 50L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.countdownProgress.progress = (millisUntilFinished / 10_000f * 100).toInt()
            }
            override fun onFinish() {
                hideVotingPanel()
            }
        }.start()
    }

    private fun hideVotingPanel() {
        countdownTimer?.cancel()
        countdownTimer = null
        binding.countdownProgress.visibility = View.GONE
        binding.eventVotingContainer.visibility = View.GONE
        removeAlarmHighlight()
    }

    // Highlights the alarm on the map using the overlay item's own icon, so the event the voting
    // panel refers to is easy to spot. Inspired by the RouteAlarms example.
    private fun highlightAlarmOnMap(overlay: OverlayItem) = SdkCall.execute {
        val mapView = binding.gemSurfaceView.mapView ?: return@execute
        val image = overlay.image ?: return@execute
        val coordinates = overlay.coordinates ?: return@execute

        val landmark = Landmark().apply {
            this.image = image
            this.coordinates = coordinates
        }
        val landmarkList = LandmarkList().apply { add(landmark) }

        val highlightSettings = HighlightRenderSettings(
            EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
        ).also {
            // Enlarge the alarm icon so the highlighted event stands out on the map.
            it.imageSize = 10.0
        }

        // Re-activating with the same id replaces any previously highlighted alarm.
        mapView.activateHighlightLandmarks(landmarkList, highlightSettings, ALARM_HIGHLIGHT_ID)
    }

    private fun removeAlarmHighlight() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.deactivateHighlight(ALARM_HIGHLIGHT_ID)
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }

    private fun playAlarmWarning(ttsText: String) {
        if (!SoundPlayingService.ttsPlayerIsInitialized) return
        SoundPlayingService.playText(ttsText, SoundPlayingListener(), SoundPlayingPreferences())
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

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    // Lays out the voting panel according to the current orientation: it spans the full width
    // across the top in portrait, or occupies a 40%-wide column pinned to the left in landscape
    // (mirroring the navigation top-panel behaviour) so the right half of the map stays visible.
    private fun applyOrientationLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val params = binding.eventVotingContainer.layoutParams as ConstraintLayout.LayoutParams
        if (isLandscape) {
            params.width = (resources.displayMetrics.widthPixels * 0.4f).toInt()
            // Detach from the end so the panel sticks to the start (left) edge.
            params.endToEnd = ConstraintLayout.LayoutParams.UNSET
        } else {
            params.width = 0 // 0dp — stretch between the start and end constraints (full width).
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        binding.eventVotingContainer.layoutParams = params
    }

    // Adjusts the GPS arrow (camera focus point) so it stays in the visible part of the map. In
    // landscape the voting panel covers the left 40% of the screen, so the focus point is shifted
    // right (0.7) to keep the arrow clear of the panel; in portrait it stays horizontally centred.
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    // Positions the Magic Lane logo (and other map UI) inside the area left free by the system
    // bars and display cutout, so it is never hidden behind system UI. Called when the map view
    // is first created and whenever the surface is resized.
    private fun updateFocusViewport() = SdkCall.runSynced {
        val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
        val viewport = mapView.viewport ?: return@runSynced
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

        val left = insets?.left ?: 0
        val top = insets?.top ?: 0
        val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
        mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
