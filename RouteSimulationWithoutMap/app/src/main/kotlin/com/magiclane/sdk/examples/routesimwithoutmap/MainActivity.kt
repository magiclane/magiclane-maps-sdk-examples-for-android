/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimwithoutmap

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
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
import com.magiclane.sdk.examples.routesimwithoutmap.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routesimwithoutmap.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RouteTrafficEvent
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.ConstVals
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.math.max
import kotlin.system.exitProcess

/**
 * Demonstrates route simulation with turn-by-turn navigation UI rendered without a map view.
 *
 * Key SDK concepts shown:
 *  - [NavigationService.startSimulation] to simulate driving along a calculated route.
 *  - [NavigationListener] callbacks to receive real-time navigation instructions,
 *    traffic events, waypoint and destination notifications, and navigation sounds.
 *  - [PositionListener] to read the simulated vehicle speed for the speed panel.
 *  - [SoundPlayingService] / [SoundUtils] for TTS voice guidance.
 *  - [GemUtilImages] to render SDK vector images (turn arrows, lane diagrams,
 *    road codes, signposts, traffic icons) into Android [Bitmap]s.
 */
class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    // Null return from getNextTurnImage / getTrafficImage means "image unchanged — skip setImageBitmap".
    // ImageUpdate(bitmap) means the image changed; bitmap may be null to clear the view.
    private data class ImageUpdate(val bitmap: Bitmap?)

    // Bundles the RouteTrafficEvent with its computed positional state so getTrafficEvent()
    // can return everything in one value instead of setting side effect member variables.
    private data class TrafficEventWithState(
        val event: RouteTrafficEvent,
        val isInsideEvent: Boolean, // true when the vehicle is currently inside the event
        val distanceToEvent: Int, // metres from current position to the event start (≥0 = ahead)
        val remainingDistanceInsideEvent: Int, // metres left to travel through the event (≥0 = inside)
    )

    private lateinit var binding: ActivityMainBinding

    // Image UID cache — avoids re-rendering and re-setting a bitmap that has not changed.
    private var lastTurnImageId = Long.MAX_VALUE
    private var lastTrafficImageId = Long.MAX_VALUE

    // Panel layout dimensions resolved once in onCreate to avoid repeated resource lookups.
    private var turnImageSize = 0
    private var topPanelWidth = 0
    private var turnMinWidth = 0
    private var navigationPanelPadding = 0
    private var lanePanelPadding = 0
    private var signPostImageSize = 0
    private var navigationImageSize = 0
    private var currentRoadCodeImageSize = 0
    private var dpi = 0

    // Current speed limit from NavigationInstruction, read by the position listener on the main thread.
    private var speedLimit = 0.0

    private val speedPanelBackgroundColor = Color.rgb(225, 55, 55)
    private val trafficPanelBackgroundColor = Color.rgb(255, 175, 63)

    // Retained across instruction updates so the traffic panel can redraw without re-fetching
    // the bitmap on every callback when the traffic image has not changed.
    private var trafficBmp: Bitmap? = null

    // Pre-rendered once on navigation start; displayed when the vehicle exits a traffic section.
    private var endOfSectionBmp: Bitmap? = null

    private val navigationService = NavigationService()

    // Kept to query total traffic delay via GemUtil.getTrafficEventsDelay on each instruction update.
    private var navRoute: Route? = null

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    // Receives periodic position updates (speed) during simulation.
    // Callbacks arrive on a background thread, so UI updates are dispatched to the main thread.
    private val positionListener = object : PositionListener() {
        override fun onNewPosition(value: PositionData) {
            if (value.hasSpeed()) {
                val speed = value.speed
                val isOverSpeeding = (speedLimit > 0.0) && (speed > speedLimit)

                val speedText = GemUtil.getSpeedText(speed, EUnitSystem.Metric)

                val currentSpeedLimit = if (speedLimit > 0.0) {
                    GemUtil.getSpeedText(speedLimit, SdkSettings.unitSystem).first
                } else {
                    ""
                }

                Util.postOnMain {
                    binding.navigationSpeedPanel.apply {
                        root.isVisible = binding.navigationTopPanel.root.isVisible && speedText.first.isNotEmpty()
                        if (root.isVisible) {
                            navSpeedLimitSign.root.isVisible = currentSpeedLimit.isNotEmpty()
                            if (currentSpeedLimit.isNotEmpty()) {
                                val defaultTextSize = resources.getDimensionPixelSize(
                                    R.dimen.nav_speed_panel_text_size,
                                ).toFloat()
                                // Shrink the text slightly when the limit is 3+ digits (e.g. "100").
                                val textSize = if (currentSpeedLimit.length >= 3) defaultTextSize * 0.8f else defaultTextSize
                                navSpeedLimitSign.navCurrentSpeedLimit.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                                navSpeedLimitSign.navCurrentSpeedLimit.text = currentSpeedLimit
                            }

                            navCurrentSpeed.text = speedText.first
                            navCurrentSpeedUnit.text = speedText.second

                            val textColor = if (isOverSpeeding) Color.WHITE else Color.BLACK

                            setBackgroundColor(
                                root.background,
                                if (isOverSpeeding) speedPanelBackgroundColor else Color.WHITE,
                            )
                            navCurrentSpeed.setTextColor(textColor)
                            navCurrentSpeedUnit.setTextColor(textColor)
                        }
                    }
                }
            }
        }
    }

    /**
     * Receives navigation events from [NavigationService].
     *
     * [NavigationListener.create] is a factory that accepts named lambda parameters for each
     * callback so only the events relevant to this example need to be implemented.
     * All callbacks are delivered on the main thread.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(

        onNavigationStarted = {
            SdkCall.execute {
                EspressoIdlingResource.increment()
                // Inform GemUtilImages of the screen DPI so rendered bitmaps are sized correctly.
                GemUtilImages.setDpi(dpi)

                // Start receiving position (speed) data now that navigation is active.
                PositionService.addListener(positionListener, EDataType.ImprovedPosition)

                // Pre-render the "end of traffic section" icon once to avoid doing it per-frame.
                endOfSectionBmp = ContextCompat.getDrawable(this, R.drawable.end_of_traffic_section)
                    ?.toBitmap(navigationImageSize, navigationImageSize)

                navRoute = navigationService.getNavigationRoute()
            }

            binding.navigationTopPanel.root.isVisible = true
            binding.bottomPanel.isVisible = true
        },

        onDestinationReached = { _: Landmark ->
            // Hide all navigation UI panels when the destination is reached.
            binding.apply {
                navigationTopPanel.root.isVisible = false
                bottomPanel.isVisible = false
                navigationLanePanel.root.isVisible = false
                currentStreetText.isVisible = false
                currentRoadCodeImageContainer.isVisible = false
                navigationSpeedPanel.root.isVisible = false
            }

            SdkCall.execute {
                PositionService.removeListener(positionListener)
            }
        },

        // Called on every navigation step change; drives all UI panel updates.
        onNavigationInstructionUpdated = { instruction ->
            updateBottomPanel(instruction)
            val turnWidth = updateTurnSection(instruction)
            // The available width for the instruction/signpost area excludes the turn panel and its margins.
            val availableWidth = topPanelWidth - turnWidth - 3 * navigationPanelPadding
            updateInstructionSection(instruction, availableWidth)
            updateLaneAndStreetSection(instruction, availableWidth)
            updateTrafficSection(instruction)
            EspressoIdlingResource.decrement()
        },

        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },

        canPlayNavigationSound = true,
    )

    // Receives routing progress events; shows/hides the progress indicator and reports errors.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false

            if (errorCode != GemError.NoError) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode) } ?: ""
                if (message.isNotEmpty()) {
                    showDialog(message)
                }
            }
        },

        postOnMain = true,
    )

    // region Navigation panel update helpers

    /**
     * Updates the bottom status bar: ETA, remaining travel time (RTT), and remaining distance (RTD).
     * Also refreshes [speedLimit] so the position listener can detect overspeed.
     * RTT label colour reflects the current total traffic delay on the route:
     * green = no delay, orange = moderate, red = significant.
     */
    private fun updateBottomPanel(instruction: NavigationInstruction) {
        var etaText = ""
        var rttText = ""
        var rtdText = ""
        var rttColor = Color.argb(255, 0, 0, 0)

        SdkCall.execute {
            speedLimit = if (instruction.navigationStatus == ENavigationStatus.Running) {
                instruction.currentStreetSpeedLimit
            } else {
                0.0
            }

            var trafficDelay = 0
            navRoute?.let {
                trafficDelay = GemUtil.getTrafficEventsDelay(it, true)
                val trafficDelayInMinutes = trafficDelay / 60
                rttColor = when {
                    trafficDelayInMinutes == 0 ->
                        Color.argb(255, 0, 170, 0) // green

                    trafficDelayInMinutes < ConstVals.BIG_TRAFFIC_DELAY_IN_MINUTES ->
                        Color.argb(255, 255, 175, 63) // orange

                    else ->
                        Color.argb(255, 235, 0, 0) // red
                }
            }

            etaText = instruction.getEta(trafficDelay)
            rttText = instruction.getRtt(trafficDelay)
            rtdText = instruction.getRtd()
        }

        binding.apply {
            eta.text = etaText
            rtt.text = rttText
            rtd.text = rtdText
            rtt.setTextColor(rttColor)
        }
    }

    /**
     * Updates the turn arrow image and the distance-to-next-turn label.
     * Returns the measured pixel width of the turn panel so the caller can compute the
     * remaining width available for the instruction/signpost area beside it.
     */
    private fun updateTurnSection(instruction: NavigationInstruction): Int {
        var instrDistance = ""
        var instrDistanceUnit = ""

        SdkCall.execute {
            GemUtil.getDistText(
                instruction.timeDistanceToNextTurn?.totalDistance ?: 0,
                EUnitSystem.Metric,
            ).let { pair ->
                instrDistance = pair.first
                instrDistanceUnit = pair.second
            }
        }

        getNextTurnImage(instruction, turnImageSize, turnImageSize)?.let { update ->
            binding.navigationTopPanel.turnImage.setImageBitmap(update.bitmap)
        }

        binding.navigationTopPanel.apply {
            turnDistance.text = instrDistance
            turnDistanceUnit.text = instrDistanceUnit
        }

        val distTextWidth = getTextWidth(binding.navigationTopPanel.turnDistance) +
            getTextWidth(binding.navigationTopPanel.turnDistanceUnit)
        return max(max(distTextWidth, turnImageSize), turnMinWidth)
    }

    /**
     * Updates the instruction area to the right of the turn panel.
     *
     * Priority order (only the first available item is shown):
     *  1. Signpost image — shown when approaching a signposted junction.
     *  2. Road code image — shown when the next road has a route number badge.
     *  3. Plain-text instruction — next street name, or manoeuvre description.
     */
    private fun updateInstructionSection(instruction: NavigationInstruction, availableWidth: Int) {
        val signPostImage = getSignpostImage(instruction, availableWidth, signPostImageSize)

        binding.navigationTopPanel.apply {
            signPost.isVisible = signPostImage != null
            signPostImage?.let {
                signPost.setImageBitmap(it)
                signPost.layoutParams.width = it.width
                signPost.layoutParams.height = it.height
            }

            // Road code and text instruction are only shown when there is no signpost.
            val showRoadCode = signPostImage == null
            roadCode.isVisible = showRoadCode

            var roadCodeDisplayed = false
            if (showRoadCode) {
                val roadCodeImage = getRoadCodeImage(instruction, availableWidth, navigationImageSize)
                roadCode.isVisible = roadCodeImage != null
                roadCodeImage?.let {
                    roadCode.setImageBitmap(it)
                    if (it.height > 0) {
                        val ratio = it.width.toFloat() / it.height
                        roadCode.layoutParams.width = (roadCode.layoutParams.height * ratio).toInt()
                    }
                    roadCodeDisplayed = true
                }
            }

            val showInstruction = signPostImage == null
            turnInstruction.isVisible = showInstruction
            if (showInstruction) {
                // Prefer the next street name; fall back to the manoeuvre description text.
                val instrText = SdkCall.execute {
                    instruction.nextStreetName?.takeIf { it.isNotEmpty() }
                        ?: instruction.nextTurnInstruction ?: ""
                } ?: ""

                turnInstruction.isVisible = instrText.isNotEmpty()
                if (instrText.isNotEmpty()) {
                    turnInstruction.text = instrText
                    // Limit to 1 line when a road code badge is already taking vertical space.
                    turnInstruction.maxLines = if (roadCodeDisplayed) 1 else 3
                }
            }
        }
    }

    /**
     * Updates the lane guidance panel and the current-road indicator below the top panel.
     *
     * Visibility priority:
     *  1. Lane diagram — shown when the SDK provides lane guidance for the current road.
     *  2. Current street name — shown when there is no lane diagram.
     *  3. Current road code badge — shown when there is no street name either.
     */
    private fun updateLaneAndStreetSection(instruction: NavigationInstruction, availableWidth: Int) {
        val availableWidthForLaneInfo = topPanelWidth - 2 * navigationPanelPadding
        val laneInfoImage = getLaneInfoImage(instruction, availableWidthForLaneInfo, navigationImageSize)

        if (laneInfoImage != null) {
            binding.currentStreetText.isVisible = false
            binding.currentRoadCodeImageContainer.isVisible = false

            binding.navigationLanePanel.apply {
                laneInformationImage.setImageBitmap(laneInfoImage)
                laneInformationImage.layoutParams.width = laneInfoImage.width
                laneInformationImage.layoutParams.height = laneInfoImage.height
                root.isVisible = true
            }
        } else {
            binding.navigationLanePanel.root.isVisible = false

            val currentStreetName = SdkCall.execute { instruction.currentStreetName } ?: ""

            if (currentStreetName.isNotEmpty()) {
                binding.currentRoadCodeImageContainer.isVisible = false
                binding.currentStreetText.isVisible = true
                binding.currentStreetText.text = currentStreetName
            } else {
                binding.currentStreetText.isVisible = false

                val currentRoadCodeImg = getRoadCodeImage(
                    instruction,
                    availableWidth,
                    currentRoadCodeImageSize,
                    false, // request the current road's code, not the next one
                )

                if (currentRoadCodeImg != null) {
                    binding.currentRoadCodeImage.setImageBitmap(currentRoadCodeImg)
                    binding.currentRoadCodeImageContainer.isVisible = true
                } else {
                    binding.currentRoadCodeImageContainer.isVisible = false
                }
            }
        }
    }

    /**
     * Updates the traffic event panel below the navigation panel.
     *
     * The panel shows the first relevant traffic event found on the remaining route.
     * "Relevant" means either the event is ahead (vehicle has not yet reached it) or the
     * vehicle is currently inside it.  When inside, the display switches to show the
     * remaining distance through the event rather than the distance to it.
     */
    private fun updateTrafficSection(instruction: NavigationInstruction) {
        binding.navigationTopPanel.apply {
            trafficPanel.isVisible = navRoute != null
            navRoute?.let { route ->
                val trafficEventWithState = getTrafficEvent(instruction, route)
                if (trafficEventWithState == null) {
                    trafficPanel.isVisible = false
                    trafficBmp = null
                    return@let
                }

                // Only update trafficBmp when the icon has actually changed; otherwise
                // keep the previous bitmap so the panel can still render it.
                val trafficImageUpdate = getTrafficImage(
                    trafficEventWithState.event,
                    navigationImageSize,
                    navigationImageSize,
                )
                if (trafficImageUpdate != null) {
                    trafficBmp = trafficImageUpdate.bitmap
                }

                trafficBmp?.let { bmp ->
                    trafficPanel.isVisible = true
                    trafficPanel.background = ContextCompat.getDrawable(
                        this@MainActivity,
                        R.drawable.bottom_rounded_white_button,
                    )

                    // Align the end-of-section overlay image exactly on top of the traffic icon.
                    val layoutParams = trafficImage.layoutParams as FrameLayout.LayoutParams
                    val margin = navigationPanelPadding
                    layoutParams.setMargins(margin, navigationPanelPadding - getSizeInPixels(1), margin, margin)
                    endOfSectionImage.layoutParams = layoutParams

                    setBackgroundColor(trafficPanel.background, trafficPanelBackgroundColor)

                    if (trafficImageUpdate != null) {
                        trafficImage.setImageBitmap(bmp)
                    }

                    val isInside = trafficEventWithState.isInsideEvent
                    // Show the end-of-section icon overlaid on the traffic icon while inside the event.
                    endOfSectionImage.isVisible = isInside && endOfSectionBmp != null
                    if (isInside) endOfSectionBmp?.let { endOfSectionImage.setImageBitmap(it) }

                    var descriptionText = ""
                    var distancePrefixText = ""
                    var distanceText = ""
                    var distanceUnitText = ""
                    var delayTimeText = ""
                    var delayTimeUnitText = ""
                    var delayDistanceText = ""
                    var delayDistanceUnitText = ""

                    SdkCall.execute {
                        val event = trafficEventWithState.event

                        descriptionText = event.description ?: ""

                        // Show remaining distance through the event when inside it,
                        // or distance to the event start when approaching it.
                        val distance = if (isInside) {
                            trafficEventWithState.remainingDistanceInsideEvent
                        } else {
                            trafficEventWithState.distanceToEvent
                        }
                        GemUtil.getDistText(distance, EUnitSystem.Metric, true).let { pair ->
                            distanceText = pair.first
                            distanceUnitText = pair.second
                        }

                        // Build the localized prefix ("in" / "out in") by formatting the
                        // resource string with an empty argument and trimming the placeholder.
                        val prefix = String.format(
                            getString(if (isInside) R.string.out_in_str else R.string.in_str),
                            "",
                        ).trim()
                        distancePrefixText = if (prefix.isEmpty()) "" else "$prefix "

                        if (!event.isRoadblock) {
                            if (isInside) {
                                // Estimate remaining delay as a proportion of total delay
                                // based on the remaining distance through the section.
                                if (event.length > 0) {
                                    val remainingTime = (event.delay * trafficEventWithState.remainingDistanceInsideEvent) / event.length
                                    GemUtil.getTimeText(remainingTime).let { pair ->
                                        delayTimeText = pair.first
                                        delayTimeUnitText = pair.second
                                    }
                                }
                            } else {
                                // When approaching, show full event length and total delay.
                                GemUtil.getDistText(event.length, SdkSettings.unitSystem, true).let { pair ->
                                    delayDistanceText = pair.first
                                    delayDistanceUnitText = pair.second
                                }
                                GemUtil.getTimeText(event.delay).let { pair ->
                                    delayTimeText = String.format("+%s", pair.first)
                                    delayTimeUnitText = pair.second
                                }
                            }
                        }
                    }

                    trafficEventDescription.text = descriptionText
                    distanceToTrafficPrefix.text = distancePrefixText
                    distanceToTraffic.text = distanceText
                    distanceToTrafficUnit.text = distanceUnitText
                    trafficDelayTime.text = delayTimeText
                    trafficDelayTimeUnit.text = delayTimeUnitText
                    trafficDelayDistance.isVisible = delayDistanceText.isNotEmpty()
                    if (delayDistanceText.isNotEmpty()) trafficDelayDistance.text = delayDistanceText
                    trafficDelayDistanceUnit.isVisible = delayDistanceUnitText.isNotEmpty()
                    if (delayDistanceUnitText.isNotEmpty()) trafficDelayDistanceUnit.text = delayDistanceUnitText
                } ?: run {
                    trafficPanel.isVisible = false
                }
            }
        }
    }

    // endregion

    // region SDK image helpers

    /**
     * Returns [ImageUpdate] with the rendered turn-arrow bitmap when the image has changed,
     * or null when the image is the same as the previous call (skip [android.widget.ImageView.setImageBitmap]).
     *
     * The arrow colors are customized: active arrow = white on black, inactive = grey on grey.
     * [GemUtilImages.asBitmap] accepts [Rgba] parameters for the active inner/outer stroke
     * and inactive inner/outer stroke to allow full colour control.
     */
    private fun getNextTurnImage(instruction: NavigationInstruction, width: Int, height: Int): ImageUpdate? {
        return SdkCall.execute {
            if (!instruction.hasNextTurnInfo()) return@execute ImageUpdate(null)
            // Skip re-rendering when the SDK image UID has not changed.
            if ((instruction.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
                return@execute null
            }

            val image = instruction.nextTurnDetails?.abstractGeometryImage
            if (image != null) {
                lastTurnImageId = image.uid
            }

            // Active arrow: white fill, black outline. Inactive: grey fill, grey outline.
            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            ImageUpdate(
                GemUtilImages.asBitmap(image, width, height, aInner, aOuter, iInner, iOuter),
            )
        }
    }

    /** Renders the signpost image for the upcoming junction, or null if none is available. */
    private fun getSignpostImage(instruction: NavigationInstruction, width: Int, height: Int): Bitmap? {
        var result: Bitmap? = null
        SdkCall.execute {
            if (instruction.hasSignpostInfo()) {
                instruction.signpostDetails?.image?.let {
                    result = GemUtilImages.asBitmap(it, width, height)
                }
            }
        }
        return result
    }

    /**
     * Renders the road code badge image (e.g. freeway shield) for either the next road
     * ([nextRoadCode] = true, default) or the current road ([nextRoadCode] = false).
     * Falls back to a 2.5× aspect-ratio width when [width] is zero.
     */
    private fun getRoadCodeImage(
        instruction: NavigationInstruction,
        width: Int,
        height: Int,
        nextRoadCode: Boolean = true,
    ): Bitmap? {
        return SdkCall.execute {
            val roadsInfo = if (nextRoadCode) {
                instruction.nextRoadInformation ?: return@execute null
            } else {
                instruction.currentRoadInformation ?: return@execute null
            }

            if (roadsInfo.isNotEmpty()) {
                var resultWidth = width
                if (resultWidth == 0) {
                    resultWidth = (2.5 * height).toInt()
                }

                val image = instruction.getRoadInfoImage(roadsInfo)

                GemUtilImages.asBitmap(image, resultWidth, height)
            } else {
                null
            }
        }
    }

    /**
     * Renders the lane guidance diagram for the current road position.
     * Colours: black background, white active lane arrows, grey inactive lane arrows.
     * Returns null when no lane data is available for the current position.
     */
    private fun getLaneInfoImage(instruction: NavigationInstruction, width: Int, height: Int): Bitmap? {
        return SdkCall.execute {
            var resultWidth = width
            if (resultWidth == 0) {
                resultWidth = (2.5 * height).toInt()
            }

            val bkColor = Rgba(0, 0, 0, 255)
            val activeColor = Rgba(255, 255, 255, 255)
            val inactiveColor = Rgba(100, 100, 100, 255)

            val image = instruction.laneImage

            GemUtilImages.asBitmap(image, resultWidth, height, bkColor, activeColor, inactiveColor)
        }
    }

    /**
     * Returns [ImageUpdate] with the traffic event icon bitmap when the image has changed,
     * or null when the UID matches the previously rendered image.
     */
    private fun getTrafficImage(from: RouteTrafficEvent?, width: Int, height: Int): ImageUpdate? = SdkCall.execute {
        if ((from?.image?.uid ?: 0) == lastTrafficImageId) return@execute null

        val image = from?.image
        if (image != null) {
            lastTrafficImageId = image.uid
        }

        ImageUpdate(GemUtilImages.asBitmap(image, width, height))
    }

    /**
     * Finds the first traffic event on the remaining route that is either ahead of the
     * vehicle or currently being traversed.
     *
     * An event is "ahead" when [distanceToEvent] ≥ 0 (vehicle has not yet reached the start).
     * An event is "inside" when [distanceToEvent] < 0 (vehicle has passed the start) AND
     * [remainingDistanceInsideEvent] ≥ 0 (vehicle has not yet passed the end).
     *
     * Returns null when navigation is not running or no relevant event is found.
     */
    private fun getTrafficEvent(instruction: NavigationInstruction, route: Route): TrafficEventWithState? =
        SdkCall.execute {
            if (instruction.navigationStatus != ENavigationStatus.Running) return@execute null
            val trafficEventsList = route.trafficEvents ?: return@execute null
            val remainingTravelDistance = instruction.remainingTravelTimeDistance?.totalDistance ?: 0

            for (event in trafficEventsList) {
                if (event.delay != 0) {
                    val distToDest = event.distanceToDestination
                    val distanceToEvent = remainingTravelDistance - distToDest
                    val remainingDistanceInsideEvent = event.length - (distToDest - remainingTravelDistance)
                    val isInsideEvent = distanceToEvent <= 0 && remainingDistanceInsideEvent >= 0

                    if (distanceToEvent >= 0 || isInsideEvent) {
                        return@execute TrafficEventWithState(
                            event,
                            isInsideEvent,
                            distanceToEvent,
                            remainingDistanceInsideEvent,
                        )
                    }
                }
            }

            null
        }

    // endregion

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        EspressoIdlingResource.increment()

        SoundUtils.addTTSPlayerInitializationListener(this)

        supportActionBar?.hide()

        dpi = resources.displayMetrics.densityDpi

        // Resolve all dimension resources once to avoid repeated resource look-ups during navigation.
        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        turnMinWidth = resources.getDimension(R.dimen.nav_top_panel_turn_min_width).toInt()
        navigationPanelPadding = resources.getDimension(R.dimen.nav_top_panel_padding).toInt()
        lanePanelPadding = resources.getDimension(R.dimen.route_status_text_lateral_padding).toInt()
        signPostImageSize = resources.getDimension(R.dimen.sign_post_image_size).toInt()
        navigationImageSize = resources.getDimension(R.dimen.navigation_image_size).toInt()
        currentRoadCodeImageSize = resources.getDimension(R.dimen.nav_top_panel_road_img_size).toInt()

        topPanelWidth = resources.displayMetrics.widthPixels

        // Set status bar color to black and icons to white
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        registerSdkListeners()

        // This step of initialization is mandatory if you want to use the SDK without a map.
        val initError = GemSdk.initSdkWithDefaults(this)
        if (initError != GemError.NoError) {
            showDialog(
                getString(R.string.sdk_init_failed, SdkCall.runSynced { GemError.getMessage(initError, this) }),
            ) { finish() }
            return
        }

        // Offset the top navigation panel to avoid overlapping system bars (notch, status bar).
        ViewCompat.setOnApplyWindowInsetsListener(binding.navigationTopPanel.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, v.paddingBottom)
            insets
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()
        SoundUtils.removeTTSPlayerInitializationListener(this)

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        // The SDK downloads the worldwide road map package on first run.
        // Wait for UpToDate before starting simulation to ensure routing data is available.
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

    // region Extension helpers for NavigationInstruction

    /** ETA = current local time + remaining travel time + any traffic delay, formatted as HH:MM. */
    @SuppressLint("DefaultLocale")
    private fun NavigationInstruction.getEta(trafficDelay: Int): String {
        val etaNumber = (remainingTravelTimeDistance?.totalTime ?: 0) + trafficDelay

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    /** Remaining travel time including traffic delay, as a localised value + unit string. */
    private fun NavigationInstruction.getRtt(trafficDelay: Int): String {
        return GemUtil.getTimeText((remainingTravelTimeDistance?.totalTime ?: 0) + trafficDelay)
            .let { pair ->
                pair.first + " " + pair.second
            }
    }

    /** Remaining travel distance, as a localised value + unit string. */
    private fun NavigationInstruction.getRtd(): String {
        return GemUtil.getDistText(
            remainingTravelTimeDistance?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // endregion

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.50732, -0.12765),
            Landmark("Paris", 48.85669, 2.35146),
        )

        val error = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (error != GemError.NoError) {
            runOnUiThread {
                showDialog(
                    getString(R.string.route_simulation_error, SdkCall.runSynced { GemError.getMessage(error, this) }),
                )
            }
        }
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        showBottomSheetDialog(
            title = getString(R.string.error),
            message = text,
            onButtonClick = { dialog ->
                onDismiss?.invoke()
                dialog.dismiss()
            },
        )
    }

    private fun showBottomSheetDialog(title: String, message: String, onButtonClick: (BottomSheetDialog) -> Unit) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
            this.message.text = message
            button.setOnClickListener {
                onButtonClick(dialog)
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

    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }

    /**
     * Sets the fill color on a drawable that may be wrapped in a [LayerDrawable].
     * [LayerDrawable] is used by the rounded-corner background drawables; the actual
     * colored shape is always at index 1.
     */
    private fun setBackgroundColor(background: Drawable, color: Int) {
        var bgnd = background

        if (background is LayerDrawable) {
            bgnd = background.getDrawable(1)
        }

        when (bgnd) {
            is ShapeDrawable -> bgnd.paint.color = color
            is GradientDrawable -> bgnd.setColor(color)
            is ColorDrawable -> bgnd.color = color
            is InsetDrawable -> (bgnd.drawable as GradientDrawable).setColor(color)
        }
    }

    @Suppress("SameParameterValue")
    private fun getSizeInPixels(dp: Int): Int {
        val metrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), metrics)
            .toInt()
    }

    private fun getTextWidth(textView: TextView, maxWidth: Int = Short.MAX_VALUE.toInt()): Int {
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        textView.measure(widthMeasureSpec, heightMeasureSpec)
        return textView.measuredWidth
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("RouteSimulationWithoutMapIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
