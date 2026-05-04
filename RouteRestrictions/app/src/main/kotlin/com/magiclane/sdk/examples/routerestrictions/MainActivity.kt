/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.routerestrictions

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
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
import com.magiclane.sdk.examples.routerestrictions.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routerestrictions.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteRestrictionType
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutePreferences
import com.magiclane.sdk.routesandnavigation.TruckProfile
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import java.util.Locale
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {
    private data class InstructionUiState(
        val instructionText: String,
        val instructionIcon: Bitmap?,
        val sameInstructionIcon: Boolean,
        val instructionDistance: String,
        val etaText: String,
        val rttText: String,
        val rtdText: String,
        val restrictionText: String,
    )

    private companion object {
        private const val INSTRUCTION_ICON_SIZE_PX = 100
        private const val TRUCK_MASS_KG = 4000
    }

    private var lastTurnImageId: Long = Long.MAX_VALUE

    private lateinit var binding: ActivityMainBinding

    private var activeDialog: BottomSheetDialog? = null

    private val navigationService = NavigationService()

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

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
                binding.restrictionPanel.isVisible = false
                binding.followGpsButton.isVisible = false
            }

            disableGPSButton()

            SdkCall.execute {
                binding.gemSurfaceView.mapView?.hideRoutes()
            }
        },
        onRestrictionsUpdated = { startDistanceM, endDistanceM ->
            SdkCall.execute {
                val sections = navRoute?.restrictionSections ?: return@execute
                val matchingSection = sections.firstOrNull { section ->
                    section.startDistanceM == startDistanceM && section.endDistanceM == endDistanceM
                } ?: sections.firstOrNull { section ->
                    section.startDistanceM <= startDistanceM && section.endDistanceM >= endDistanceM
                }
                val restrictionText = matchingSection?.restrictions?.toRestrictionText().orEmpty()
                runOnUiThread {
                    if (restrictionText.isNotEmpty()) {
                        binding.restrictionPanel.isVisible = true
                        binding.restrictionText.text = restrictionText
                    }
                }
            }
        },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true,
    )

    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
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
        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        SoundUtils.addTTSPlayerInitializationListener(this)
        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            dismissActiveDialog()
            clearSdkListeners()
            SoundUtils.removeTTSPlayerInitializationListener(this)
            GemSdk.release()
            exitProcess(0)
        }
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
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
    }

    private fun enableGPSButton() {
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                    binding.topPanel.isVisible = false
                    binding.bottomPanel.isVisible = false
                    binding.restrictionPanel.isVisible = false
                }

                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false
                    binding.topPanel.isVisible = true
                    binding.bottomPanel.isVisible = true
                }

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
            Landmark("Start", 45.65894, 25.57802),
            Landmark("Stop", 45.66619, 25.61499),
        )

        val routePreferences = RoutePreferences().apply {
            transportMode = ERouteTransportMode.Lorry
            truckProfile = TruckProfile(massKg = TRUCK_MASS_KG)
        }

        val error = navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
            routePreferences,
            speedMultiplier = 3.0f,
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
        val currentRoute = navRoute
        val restrictionsMask = currentRestrictions

        var sameTurnImage = false
        val instrIcon = getNextTurnImage(this, INSTRUCTION_ICON_SIZE_PX, INSTRUCTION_ICON_SIZE_PX) { isSame ->
            sameTurnImage = isSame
        }

        return InstructionUiState(
            instructionText = nextStreetName ?: (nextTurnInstruction ?: ""),
            instructionIcon = instrIcon,
            sameTurnImage,
            instructionDistance = getDistanceInMeters(),
            etaText = currentRoute?.getEta().orEmpty(),
            rttText = currentRoute?.getRtt().orEmpty(),
            rtdText = currentRoute?.getRtd().orEmpty(),
            restrictionText = restrictionsMask.toRestrictionText(),
        )
    }

    private fun renderInstructionUi(uiState: InstructionUiState) {
        binding.apply {
            navInstruction.text = uiState.instructionText
            if (!uiState.sameInstructionIcon) {
                navInstructionIcon.setImageBitmap(uiState.instructionIcon)
            }
            navInstructionDistance.text = uiState.instructionDistance

            eta.text = uiState.etaText
            rtt.text = uiState.rttText
            rtd.text = uiState.rtdText

            val hasRestrictions = uiState.restrictionText.isNotEmpty()
            restrictionPanel.isVisible = hasRestrictions && topPanel.isVisible
            if (hasRestrictions) {
                restrictionText.text = uiState.restrictionText
            }
        }
    }

    private fun Int.toRestrictionText(): String {
        if (this == 0) return ""
        val labels = ERouteRestrictionType.entries
            .filter { type -> type.value > 0 && (this and type.value) == type.value }
            .map { it.name }
            .distinct()
        if (labels.isEmpty()) return ""
        return getString(R.string.restrictions_label, labels.joinToString(", "))
    }

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        onSameImage: (Boolean) -> Unit = {},
    ): Bitmap? {
        if (!navInstr.hasNextTurnInfo()) return null

        if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
            onSameImage(true)
            return null
        }

        val image = navInstr.nextTurnDetails?.abstractGeometryImage
        if (image != null) {
            lastTurnImageId = image.uid
        }

        val aInner = Rgba(255, 255, 255, 255)
        val aOuter = Rgba(0, 0, 0, 255)
        val iInner = Rgba(128, 128, 128, 255)
        val iOuter = Rgba(128, 128, 128, 255)

        return GemUtilImages.asBitmap(
            image,
            width,
            height,
            aInner,
            aOuter,
            iInner,
            iOuter,
        )
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

    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage(getString(R.string.tts_language_eng_usa))
    }

    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}
