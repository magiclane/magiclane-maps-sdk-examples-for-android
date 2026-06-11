/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.routerestrictions

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
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
import kotlin.math.max
import kotlin.math.min
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

    // Captured once at portrait orientation and reused as the base for all subsequent orientation
    // constraint adjustments, so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private var activeDialog: BottomSheetDialog? = null

    private val navigationService = NavigationService()

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    private var restrictions = 0

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
            applyCameraFocus()
            runOnUiThread {
                setNavigationPanelsVisible(true)
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
                setNavigationPanelsVisible(false)
                binding.followGpsButton.isVisible = false
            }

            disableGPSButton()

            SdkCall.execute {
                binding.gemSurfaceView.mapView?.hideRoutes()
            }
        },
        onRestrictionsUpdated = { _, enterRestrictions ->
            restrictions = enterRestrictions
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
                        SdkCall.runSynced { GemError.getMessage(errorCode, this) },
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

        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val panelMargin = resources.getDimensionPixelSize(R.dimen.big_padding)
            val setPanelHorizontalMargins = { panel: ConstraintLayout ->
                val params = panel.layoutParams as ConstraintLayout.LayoutParams
                params.marginStart = panelMargin
                params.marginEnd = panelMargin
                panel.layoutParams = params
            }

            setPanelHorizontalMargins(binding.topPanel)
            setPanelHorizontalMargins(binding.restrictionPanel)
            setPanelHorizontalMargins(binding.bottomPanel)
        }

        SoundUtils.addTTSPlayerInitializationListener(this)
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

    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels
        // were GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val restrictionVis = binding.restrictionPanel.visibility
        val bottomVis = binding.bottomPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.big_padding)
        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()

                for (id in intArrayOf(R.id.top_panel, R.id.restriction_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(id, ConstraintSet.END)
                }
            } else {
                for (id in intArrayOf(R.id.top_panel, R.id.restriction_panel, R.id.bottom_panel)) {
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
        }.applyTo(rootLayout)

        binding.topPanel.visibility = topVis
        binding.restrictionPanel.visibility = restrictionVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis
    }

    // this adjusts GPS arrow position on map
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // In landscape the navigation panel occupies the left 40 % of the screen, so shift the
            // camera focus point right (0.7) to keep the arrow in the visible map area.
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    // this adjusts Magic Lane logo position on map
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            val w = max(width, height)
            val h = min(width, height)

            val left = if (binding.topPanel.isVisible) binding.topPanel.right else insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            Rect(left, top, right, bottom)
        } else {
            val w = min(width, height)
            val h = max(width, height)

            val left = insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val top = when {
                binding.topPanel.isVisible && binding.restrictionPanel.isVisible -> binding.restrictionPanel.bottom
                binding.topPanel.isVisible -> binding.topPanel.bottom
                else -> insets?.top ?: 0
            }
            val bottom = if (binding.bottomPanel.isVisible) {
                binding.bottomPanel.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            binding.restrictionPanel.isVisible = false
            updateFocusViewport()
        } else {
            binding.root.post { updateFocusViewport() }
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

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
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
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    private fun enableGPSButton() {
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                    setNavigationPanelsVisible(false)
                }

                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false
                    setNavigationPanelsVisible(true)
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
            runOnUiThread {
                showDialog(
                    getString(
                        R.string.failed_to_start_simulation,
                        SdkCall.runSynced { GemError.getMessage(error, this) },
                    ),
                )
            }
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
        val restrictionsMask = restrictions

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
            restrictionText = if (restrictionsMask != 0) {
                restrictionsMask.toRestrictionText()
            } else {
                getUpcomingRestrictionText(currentRoute, remainingTravelTimeDistance?.totalDistance ?: 0)
            },
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

    private fun Int.toRestrictionTypeNames(): List<String> {
        if (this == 0) return emptyList()
        return ERouteRestrictionType.entries
            .filter { type -> type.value > 0 && (this and type.value) == type.value }
            .map { it.name }
            .distinct()
    }

    private fun Int.toRestrictionText(): String {
        val labels = toRestrictionTypeNames()
        if (labels.isEmpty()) return ""
        return getString(R.string.restrictions_label, labels.joinToString(", "))
    }

    private fun getUpcomingRestrictionText(route: Route?, remainingDist: Int): String {
        route ?: return ""
        val sections = route.restrictionSections ?: return ""
        val totalDist = route.getTimeDistance(false)?.totalDistance ?: return ""
        val distFromStart = totalDist - remainingDist
        val nextSection = sections
            .filter { it.startDistanceM > distFromStart }
            .minByOrNull { it.startDistanceM }
            ?: return ""
        val typeNames = nextSection.restrictions.toRestrictionTypeNames()
        if (typeNames.isEmpty()) return ""
        val (distValue, distUnit) = GemUtil.getDistText(
            nextSection.startDistanceM - distFromStart,
            EUnitSystem.Metric,
        )
        return getString(R.string.upcoming_restrictions_label, distValue, distUnit, typeNames.joinToString(", "))
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
