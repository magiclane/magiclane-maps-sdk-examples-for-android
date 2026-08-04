/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimulationcompose

import android.annotation.SuppressLint
import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RouteTrafficEvent
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import java.util.Locale

class RouteSimulationModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    var errorMessage by mutableStateOf("")
    var progressBarIsVisible by mutableStateOf(false)
    var followGpsButtonIsVisible by mutableStateOf(false)
    var navigationPanelsAreVisible by mutableStateOf(false)

    var instrText by mutableStateOf("")
    var instrDistance by mutableStateOf("")
    var etaText by mutableStateOf("")
    var rttText by mutableStateOf("")
    var rtdText by mutableStateOf("")
    var statusMessage by mutableStateOf("")

    var turnImage: ImageBitmap? by mutableStateOf(null)
    var signPostImage: ImageBitmap? by mutableStateOf(null)
    var roadCodeImage: ImageBitmap? by mutableStateOf(null)

    var trafficPanelVisible by mutableStateOf(false)
    var trafficImage: ImageBitmap? by mutableStateOf(null)
    var endOfSectionImage: ImageBitmap? by mutableStateOf(null)
    var endOfSectionVisible by mutableStateOf(false)
    var trafficEventDescription by mutableStateOf("")
    var distanceToTrafficPrefix by mutableStateOf("")
    var distanceToTrafficText by mutableStateOf("")
    var distanceToTrafficUnitText by mutableStateOf("")
    var trafficDelayTimeText by mutableStateOf("")
    var trafficDelayTimeUnitText by mutableStateOf("")
    var trafficDelayDistanceText by mutableStateOf("")
    var trafficDelayDistanceUnitText by mutableStateOf("")

    var turnImageSize: Int = 0
    var navigationImageSize: Int = 0
    var signPostImageSize: Int = 0
    var signPostHeightPx: Int = 0
    var turnPaddingPx: Int = 0
    var topPanelWidthPx: Int = 0
    var distanceTextWidthPx: Int = 0

    // Camera focus and focus viewport support
    var topPanelBottomPx: Int = 0
    var topPanelRightPx: Int = 0
    var trafficPanelBottomPx: Int = 0
    var bottomPanelTopPx: Int = 0

    @SuppressLint("StaticFieldLeak")
    private var gemSurfaceView: GemSurfaceView? = null
    private val navigationService = NavigationService()
    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    private val playingListener = object : SoundPlayingListener() {}
    private val soundPreference = SoundPlayingPreferences()

    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var lastTrafficImageId: Long = Long.MAX_VALUE
    private var navigationStatus = ENavigationStatus.Running

    private var distToTrafficEvent = 0
    private var remainingDistInsideTrafficEvent = 0
    private var insideTrafficEvent = false

    private lateinit var navigationListener: NavigationListener

    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBarIsVisible = true
        },
        onCompleted = { errorCode, _ ->
            progressBarIsVisible = false

            if (errorCode != GemError.NoError) {
                errorMessage = app.getString(
                    R.string.route_simulation_error,
                    SdkCall.runSynced { GemError.getMessage(errorCode, app) },
                )
            }
        },
        postOnMain = true,
    )

    fun initialize(gemSurfaceView: GemSurfaceView?, endOfSectionBmp: Bitmap?) {
        this.gemSurfaceView = gemSurfaceView
        endOfSectionImage = endOfSectionBmp?.asImageBitmap()

        navigationListener = NavigationListener.create(
            onNavigationStarted = {
                SdkCall.execute {
                    gemSurfaceView?.mapView?.let { mapView ->
                        mapView.preferences?.enableCursor = false
                        navRoute?.let { route -> mapView.presentRoute(route) }

                        mapView.onExitFollowingPosition = {
                            followGpsButtonIsVisible = true
                            navigationPanelsAreVisible = false
                        }
                        mapView.onEnterFollowingPosition = {
                            followGpsButtonIsVisible = false
                            val simulationIsActive =
                                SdkCall.execute { navigationService.isSimulationActive(navigationListener) } ?: false
                            if (simulationIsActive) {
                                navigationPanelsAreVisible = true
                            }
                        }

                        mapView.followPosition()
                    }
                }
                applyCameraFocus()
                navigationPanelsAreVisible = true
            },
            onNavigationInstructionUpdated = { instr ->
                updateNavigationInstruction(instr)
            },
            onDestinationReached = {
                onNavigationEnded()
            },
            onNotifyStatusChange = { status ->
                navigationStatus = status
                refreshStatusMessage()
            },
            onNavigationError = { error ->
                onNavigationEnded(error)
            },
            onNavigationSound = { sound ->
                SdkCall.execute {
                    SoundPlayingService.play(sound, playingListener, soundPreference)
                }
            },
            canPlayNavigationSound = true,
        )
    }

    private fun onNavigationEnded(errorCode: Int = GemError.NoError) {
        if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
            errorMessage = app.getString(
                R.string.route_simulation_error,
                SdkCall.runSynced { GemError.getMessage(errorCode, app) },
            )
        }
        navigationPanelsAreVisible = false
        trafficPanelVisible = false
    }

    private fun updateNavigationInstruction(instruction: NavigationInstruction) {
        SdkCall.execute {
            val navInstrText = instruction.nextStreetName?.takeIf { it.isNotEmpty() }
                ?: instruction.nextTurnInstruction.orEmpty()

            instrText = navInstrText
            instrDistance = instruction.getDistanceInMeters()

            navRoute?.apply {
                etaText = getEta()
                rttText = getRtt()
                rtdText = getRtd()
            }

            updateTurnImage(instruction)
            updateSignPostAndRoadCode(instruction)
            updateTrafficPanel(instruction)
        }
    }

    private fun updateTurnImage(instruction: NavigationInstruction) {
        if (!instruction.hasNextTurnInfo()) {
            turnImage = null
            return
        }

        val imageUid = instruction.nextTurnDetails?.abstractGeometryImage?.uid ?: 0
        if (imageUid == lastTurnImageId) return

        val image = instruction.nextTurnDetails?.abstractGeometryImage
        if (image != null) {
            lastTurnImageId = image.uid
        }

        val bmp = GemUtilImages.asBitmap(
            image,
            turnImageSize,
            turnImageSize,
            Rgba(255, 255, 255, 255),
            Rgba(0, 0, 0, 255),
            Rgba(128, 128, 128, 255),
            Rgba(128, 128, 128, 255),
        )
        turnImage = bmp?.asImageBitmap()
    }

    private fun updateSignPostAndRoadCode(instruction: NavigationInstruction) {
        val availableWidth = if (topPanelWidthPx > 0) {
            topPanelWidthPx - maxOf(turnImageSize, distanceTextWidthPx) - 3 * turnPaddingPx
        } else {
            0
        }
        val signPostHeight = if (signPostHeightPx > 0) signPostHeightPx else signPostImageSize
        val signPost = getSignpostImage(instruction, availableWidth, signPostHeight)
        signPostImage = signPost?.asImageBitmap()

        roadCodeImage = if (signPost == null) {
            getRoadCodeImage(instruction, availableWidth, navigationImageSize)?.asImageBitmap()
        } else {
            null
        }
    }

    private fun getSignpostImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        if (!navInstr.hasSignpostInfo()) return null
        val distToNextTurn = navInstr.timeDistanceToNextTurn?.totalDistance ?: 0
        if (distToNextTurn > 1000) return null
        return navInstr.signpostDetails?.image?.let { image ->
            GemUtilImages.asBitmap(image, width, height)
        }
    }

    private fun getRoadCodeImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        val roadsInfo = navInstr.nextRoadInformation ?: return null
        if (roadsInfo.isEmpty()) return null
        val resultWidth = if (width == 0) (2.5 * height).toInt() else width
        val image = navInstr.getRoadInfoImage(roadsInfo)
        return GemUtilImages.asBitmap(image, resultWidth, height)
    }

    private fun refreshStatusMessage() {
        statusMessage = when (navigationStatus) {
            ENavigationStatus.WaitingRoute -> {
                when (navRoute?.status) {
                    ERouteStatus.WaitingInternetConnection -> app.getString(R.string.waiting_for_internet_connection)
                    ERouteStatus.Calculating -> app.getString(R.string.calculating)
                    ERouteStatus.Ready -> app.getString(R.string.gps_accuracy_not_good_enough)
                    else -> app.getString(R.string.calculating)
                }
            }
            ENavigationStatus.WaitingGPS -> {
                if (navigationService.isSimulationActive(navigationListener)) {
                    app.getString(R.string.calculating)
                } else {
                    app.getString(R.string.getting_position)
                }
            }
            else -> ""
        }
    }

    private fun updateTrafficPanel(instruction: NavigationInstruction) {
        if (!navigationPanelsAreVisible) {
            trafficPanelVisible = false
            return
        }

        val route = navRoute ?: run {
            trafficPanelVisible = false
            return
        }

        val trafficEvent = getTrafficEvent(instruction, route) ?: run {
            trafficPanelVisible = false
            trafficImage = null
            return
        }

        updateTrafficEventInfo(trafficEvent)
        trafficPanelVisible = trafficImage != null
    }

    private fun getTrafficEvent(navInstr: NavigationInstruction, route: Route): RouteTrafficEvent? = SdkCall.execute {
        if (navInstr.navigationStatus != ENavigationStatus.Running) return@execute null
        val trafficEventsList = route.trafficEvents ?: return@execute null

        val remainingTravelDistance = navInstr.remainingTravelTimeDistance?.totalDistance ?: 0

        for (event in trafficEventsList) {
            if (event.delay != 0) {
                val distToDest = event.distanceToDestination
                distToTrafficEvent = remainingTravelDistance - distToDest

                insideTrafficEvent = false

                if (distToTrafficEvent <= 0) {
                    remainingDistInsideTrafficEvent = event.length - (distToDest - remainingTravelDistance)
                    if (remainingDistInsideTrafficEvent >= 0) {
                        insideTrafficEvent = true
                    }
                }

                if ((distToTrafficEvent >= 0) || (remainingDistInsideTrafficEvent >= 0)) {
                    return@execute event
                }
            }
        }

        return@execute null
    }

    private fun updateTrafficEventInfo(trafficEvent: RouteTrafficEvent) = SdkCall.execute {
        trafficEventDescription = trafficEvent.description ?: ""

        val distance = if (insideTrafficEvent) remainingDistInsideTrafficEvent else distToTrafficEvent
        val distancePair = GemUtil.getDistText(distance, EUnitSystem.Metric, true)
        distanceToTrafficText = distancePair.first
        distanceToTrafficUnitText = distancePair.second

        val prefixRes = if (insideTrafficEvent) R.string.out_in_str else R.string.in_str
        distanceToTrafficPrefix = app.getString(prefixRes, "").trim() + " "

        trafficDelayDistanceText = ""
        trafficDelayDistanceUnitText = ""
        trafficDelayTimeText = ""
        trafficDelayTimeUnitText = ""

        if (!trafficEvent.isRoadblock) {
            if (insideTrafficEvent) {
                if (trafficEvent.length > 0) {
                    val remainingDelay = (trafficEvent.delay * remainingDistInsideTrafficEvent) / trafficEvent.length
                    val timePair = GemUtil.getTimeText(remainingDelay)
                    trafficDelayTimeText = timePair.first
                    trafficDelayTimeUnitText = timePair.second
                }
            } else {
                val distPair = GemUtil.getDistText(trafficEvent.length, SdkSettings.unitSystem, true)
                trafficDelayDistanceText = distPair.first
                trafficDelayDistanceUnitText = distPair.second

                val timePair = GemUtil.getTimeText(trafficEvent.delay)
                trafficDelayTimeText = String.format("+%s", timePair.first)
                trafficDelayTimeUnitText = timePair.second
            }
        }

        val newBmp = getTrafficImage(trafficEvent, navigationImageSize, navigationImageSize)
        if (newBmp != null) {
            trafficImage = newBmp.asImageBitmap()
        }

        endOfSectionVisible = insideTrafficEvent && endOfSectionImage != null
    }

    private fun getTrafficImage(from: RouteTrafficEvent?, width: Int, height: Int): Bitmap? = SdkCall.execute {
        if ((from?.image?.uid ?: 0) == lastTrafficImageId) return@execute null

        val image = from?.image
        if (image != null) {
            lastTrafficImageId = image.uid
        }

        GemUtilImages.asBitmap(image, width, height)
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + pair.second }
    }

    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0
        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format(Locale.getDefault(), "%d:%02d", time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(this.getTimeDistance(true)?.totalTime ?: 0)
            .let { pair -> pair.first + " " + pair.second }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
    }

    fun applyCameraFocus() = SdkCall.runSynced {
        val view = gemSurfaceView ?: return@runSynced
        val isLandscape = view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        view.mapView?.preferences?.followPositionPreferences?.cameraFocus =
            if (isLandscape) XyF(0.725f, 0.75f) else XyF(0.5f, 0.75f)
    }

    fun updateFocusViewport() {
        val view = gemSurfaceView ?: return
        val insets = ViewCompat.getRootWindowInsets(view)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        ) ?: return
        val isLandscape = view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val l = insets.left
        val t = insets.top
        val r = insets.right
        val b = insets.bottom

        SdkCall.runSynced {
            val screenWidthPx = view.mapView?.viewport?.width ?: 0
            val screenHeightPx = view.mapView?.viewport?.height ?: 0

            if (screenWidthPx == 0 || screenHeightPx == 0) return@runSynced

            val rect = if (isLandscape) {
                val left = if (navigationPanelsAreVisible && topPanelRightPx > 0) topPanelRightPx else l
                val right = (screenWidthPx - r).coerceAtLeast(left)
                val bottom = (screenHeightPx - b).coerceAtLeast(t)
                Rect(left, t, right, bottom)
            } else {
                val right = (screenWidthPx - r).coerceAtLeast(l)
                val top = when {
                    navigationPanelsAreVisible && trafficPanelVisible && trafficPanelBottomPx > 0 -> trafficPanelBottomPx
                    navigationPanelsAreVisible && topPanelBottomPx > 0 -> topPanelBottomPx
                    else -> t
                }
                val bottom = if (navigationPanelsAreVisible && bottomPanelTopPx > 0) {
                    bottomPanelTopPx.coerceAtLeast(top)
                } else {
                    (screenHeightPx - b).coerceAtLeast(top)
                }
                Rect(l, top, right, bottom)
            }

            view.mapView?.preferences?.focusViewport = rect
        }
    }

    fun startFollowingPosition(gemSurfaceView: GemSurfaceView?) = SdkCall.execute {
        gemSurfaceView?.mapView?.followPosition()
    }

    fun startSimulation() = SdkCall.execute {
        if (navigationService.isSimulationActive(navigationListener)) return@execute

        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        val error = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (error != GemError.NoError) {
            errorMessage = app.getString(
                R.string.route_simulation_error,
                GemError.getMessage(error, app),
            )
        }
    }
}
