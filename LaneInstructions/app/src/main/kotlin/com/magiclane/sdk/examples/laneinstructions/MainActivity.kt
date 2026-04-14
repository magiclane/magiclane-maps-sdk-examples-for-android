/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.laneinstructions

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.laneinstructions.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.laneinstructions.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import java.util.Locale
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {
    private data class InstructionUiState(
        val instructionText: String,
        val instructionIcon: Bitmap?,
        val instructionDistance: String,
        val lanesBitmap: Bitmap?,
        val etaText: String,
        val rttText: String,
        val rtdText: String,
    )

    private companion object {
        private const val INSTRUCTION_ICON_SIZE_PX = 100
        private const val API_TOKEN_DIALOG_DELAY_MS = 500L
    }

    private lateinit var binding: ActivityMainBinding

    private var lanePanelHeight = 0
    private var availableWidth = 0
    private var emptyApiToken = false
    private var activeDialog: BottomSheetDialog? = null

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }
            runOnUiThread {
                binding.topPanel.isVisible = true
                binding.bottomPanel.isVisible = true
                binding.statusText.isVisible = false
            }
        },
        onNavigationInstructionUpdated = { instr ->
            SdkCall.execute {
                val uiState = instr.toUiState()
                runOnUiThread {
                    renderInstructionUi(uiState)
                }
            }
        },
        onDestinationReached = {
            runOnUiThread {
                binding.topPanel.isVisible = false
                binding.bottomPanel.isVisible = false
                binding.followGpsButton.isVisible = false
            }

            disableGPSButton()

            SdkCall.execute {
                binding.gemSurfaceView.mapView?.hideRoutes()
            }

            showStatusMessage(getString(R.string.destination_reached))
        },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
            showStatusMessage(getString(R.string.routing_process_started))
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false
            if (errorCode != GemError.NoError) {
                showDialog(
                    getString(
                        R.string.routing_process_failed,
                        GemError.getMessage(errorCode, this),
                    ),
                )
            }
        },
        postOnMain = true
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupTts()
        setupLaneMetrics()
        setupGemSurfaceCallbacks()
    }

    override fun onStop() {
        super.onStop()
        // Release the SDK.
        if (isFinishing) {
            dismissActiveDialog()
            clearGemSurfaceCallbacks()
            SoundUtils.removeTTSPlayerInitializationListener(this)
            GemSdk.release()
            exitProcess(0)
        }
    }

    private fun setupTts() {
        SoundUtils.addTTSPlayerInitializationListener(this)
    }

    private fun setupLaneMetrics() {
        lanePanelHeight = resources.getDimension(R.dimen.lane_panel_height).toInt()
        availableWidth = resources.displayMetrics.widthPixels -
            2 * resources.getDimension(R.dimen.big_padding).toInt() -
            2 * resources.getDimension(R.dimen.medium_padding).toInt()
    }

    private fun setupGemSurfaceCallbacks() {
        binding.gemSurfaceView.onSdkInitSucceeded = {
            emptyApiToken = SdkSettings.appAuthorization.isNullOrEmpty()
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            if (emptyApiToken) {
                Util.postOnMainDelayed({
                    showDialog(getString(R.string.missing_app_authorization)) {
                        finish()
                    }
                }, API_TOKEN_DIALOG_DELAY_MS)
            } else {
                startSimulation()
            }
        }
    }

    private fun clearGemSurfaceCallbacks() {
        binding.gemSurfaceView.onSdkInitSucceeded = null
        binding.gemSurfaceView.onDefaultMapViewCreated = null
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                }

                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false
                }

                // Set on click action for the GPS button.
                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    private fun disableGPSButton() {
        binding.gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = null
            onEnterFollowingPosition = null
            binding.followGpsButton.setOnClickListener(null)
            binding.followGpsButton.isVisible = false
        }
    }

    private fun startSimulation() {
        val waypoints = arrayListOf(
            Landmark("Calea Bucuresti", 45.64924625, 25.6180490625),
            Landmark("Harmanului", 45.6549909375, 25.6161609375)
        )
        val error = navigationService.startSimulation(

            waypoints,
            navigationListener,
            routingProgressListener,
        )

        if (error != GemError.NoError) {
            showDialog(
                getString(
                    R.string.failed_to_start_simulation,
                    GemError.getMessage(error, this),
                ),
            )
        }
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        dismissActiveDialog()
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
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) {
                activeDialog = null
            }
        }
    }

    private fun dismissActiveDialog() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun NavigationInstruction.toUiState(): InstructionUiState {
        val lanesBitmap = laneImage?.asBitmap(
            availableWidth,
            lanePanelHeight,
            activeColor = Rgba.white(),
        )

        val currentRoute = navRoute
        return InstructionUiState(
            instructionText = nextStreetName ?: (nextTurnInstruction ?: ""),
            instructionIcon = nextTurnImage?.asBitmap(INSTRUCTION_ICON_SIZE_PX, INSTRUCTION_ICON_SIZE_PX),
            instructionDistance = getDistanceInMeters(),
            lanesBitmap = lanesBitmap,
            etaText = currentRoute?.getEta().orEmpty(),
            rttText = currentRoute?.getRtt().orEmpty(),
            rtdText = currentRoute?.getRtd().orEmpty(),
        )
    }

    private fun renderInstructionUi(uiState: InstructionUiState) {
        binding.apply {
            navInstruction.text = uiState.instructionText
            navInstructionIcon.setImageBitmap(uiState.instructionIcon)
            navInstructionDistance.text = uiState.instructionDistance

            eta.text = uiState.etaText
            rtt.text = uiState.rttText
            rtd.text = uiState.rtdText

            laneContainer.isVisible = uiState.lanesBitmap != null
            uiState.lanesBitmap?.let { bitmap ->
                laneImage.setImageBitmap(bitmap)
                laneImage.layoutParams.width = bitmap.width
                laneImage.layoutParams.height = bitmap.height
            }
        }
    }

    private fun showStatusMessage(text: String) {
        binding.statusText.isVisible = true
        binding.statusText.text = text
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).toDisplayString()
    }

    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format(Locale.getDefault(), getString(R.string.time_format_hh_mm), time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).toDisplayString()
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).toDisplayString()
    }

    private fun Pair<String, String>.toDisplayString(): String {
        return "$first $second"
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage(getString(R.string.tts_language_eng_usa))
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}

