/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimulation

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textview.MaterialTextView
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
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
import com.magiclane.sdk.examples.routesimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routesimulation.databinding.DialogLayoutBinding
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
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private data class NavigationUiData(
        val instructionText: String = "",
        val instructionIcon: Bitmap? = null,
        val hasSameTurnImage: Boolean = false,
        val instructionDistance: String = "",
        val etaText: String = "",
        val rttText: String = "",
        val rttColor: Int = Color.WHITE,
        val rtdText: String = "",
        val signPostBitmap: Bitmap? = null,
        val roadCodeBitmap: Bitmap? = null,
    )

    private lateinit var binding: ActivityMainBinding

    private var turnPadding: Int = 0
    private var turnImageSize: Int = 0
    private var navigationPanelPadding: Int = 0
    private var signPostImageSize: Int = 0
    private var topPanelWidth: Int = 0

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    // Long.MAX_VALUE ensures the first real image UID never matches, so it is always rendered.
    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var lastTrafficImageId: Long = Long.MAX_VALUE

    private val trafficPanelBackgroundColor = Color.rgb(255, 175, 63)
    private var sameTrafficImage = false
    private var trafficBmp: Bitmap? = null
    private var endOfSectionBmp: Bitmap? = null
    private var distToTrafficEvent = 0
    private var remainingDistInsideTrafficEvent = 0
    private var insideTrafficEvent = false
    private var trafficEventDescriptionText = ""
    private var distanceToTrafficPrefixText = ""
    private var trafficDelayTimeText = ""
    private var trafficDelayTimeUnitText = ""
    private var trafficDelayDistanceText = ""
    private var trafficDelayDistanceUnitText = ""
    private var distanceToTrafficText = ""
    private var distanceToTrafficUnitText = ""
    private var navigationImageSize: Int = 0

    // Captured once at portrait orientation and reused as the base for all subsequent orientation
    // constraint adjustments, so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private var navigationStatus = ENavigationStatus.Running

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     * We will use just the onNavigationStarted method, but for more available
     * methods you should check the documentation.
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
            applyCameraFocus()
            endOfSectionBmp = ContextCompat.getDrawable(this@MainActivity, R.drawable.end_of_traffic_section)
                ?.toBitmap(navigationImageSize, navigationImageSize)
            setNavigationPanelsVisible(isVisible = true)
        },
        onNavigationInstructionUpdated = { instr -> updateNavigationInstruction(instr) },
        onDestinationReached = { onNavigationEnded() },
        onNotifyStatusChange = { status ->
            navigationStatus = status
            refreshStatusMessage()
        },
        onNavigationError = { error -> onNavigationEnded(error) },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true,
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SoundUtils.addTTSPlayerInitializationListener(this)

        turnPadding = resources.getDimension(R.dimen.nav_top_panel_turn_margin).toInt()
        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        navigationImageSize = resources.getDimension(R.dimen.navigation_image_size).toInt()
        navigationPanelPadding = resources.getDimension(R.dimen.nav_top_panel_padding).toInt()
        signPostImageSize = resources.getDimension(R.dimen.sign_post_image_size).toInt()

        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
            val setPanelHorizontalMargins = { panel: ConstraintLayout ->
                val params = panel.layoutParams as ConstraintLayout.LayoutParams
                params.marginStart = panelMargin
                params.marginEnd = panelMargin
                panel.layoutParams = params
            }

            setPanelHorizontalMargins(binding.topPanel)
            setPanelHorizontalMargins(binding.bottomPanel)
            setPanelHorizontalMargins(binding.trafficPanel)
        }

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
        val bottomVis = binding.bottomPanel.visibility
        val trafficVis = binding.trafficPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                topPanelWidth = panelWidth

                for (id in intArrayOf(R.id.top_panel, R.id.traffic_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(id, ConstraintSet.END)
                }
            } else {
                topPanelWidth = resources.displayMetrics.widthPixels - 2 * navigationPanelPadding

                for (id in intArrayOf(R.id.top_panel, R.id.traffic_panel, R.id.bottom_panel)) {
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
        }.applyTo(rootLayout)

        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
        binding.trafficPanel.visibility = trafficVis
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

    fun getFocusViewport(): Rect {
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
                binding.topPanel.isVisible && binding.trafficPanel.isVisible -> binding.trafficPanel.bottom
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

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()

        // Release the SDK.
        GemSdk.release()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
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

        // Delay simulation start until the worldwide road map is fully downloaded and up to date;
        // the callback is cleared immediately after firing to avoid repeat invocations.
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
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.visibility = View.VISIBLE
                    setNavigationPanelsVisible(isVisible = false)
                }

                onEnterFollowingPosition = {
                    followGpsButton.visibility = View.GONE

                    val simulationIsActive = SdkCall.execute { navigationService.isSimulationActive() } ?: false
                    if (simulationIsActive) {
                        setNavigationPanelsVisible(isVisible = true)
                    }
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

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: ""
                if (message.isNotEmpty()) {
                    showDialog(message)
                }
            }
            setNavigationPanelsVisible(isVisible = false)
            disableGPSButton()
        }

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
    }

    private fun updateNavigationInstruction(instruction: NavigationInstruction) {
        val distanceToNextTurnWidth = getTextViewWidth(binding.instrDistance, "9000 km")
        val availableWidth = topPanelWidth - max(turnImageSize, distanceToNextTurnWidth) - 3 * turnPadding

        val navData = SdkCall.execute { collectNavigationUiData(instruction, availableWidth) } ?: return

        binding.apply {
            if (!navData.hasSameTurnImage) {
                navIcon.setImageBitmap(navData.instructionIcon)
            }
            instrDistance.text = navData.instructionDistance
            eta.text = navData.etaText
            rtt.text = navData.rttText
            rtt.setTextColor(navData.rttColor)
            rtd.text = navData.rtdText

            val hasSignPost = navData.signPostBitmap != null
            val hasRoadCode = navData.roadCodeBitmap != null

            signPost.isVisible = hasSignPost
            navData.signPostBitmap?.let { bmp ->
                signPost.setImageBitmap(bmp)
                signPost.layoutParams.width = bmp.width
                signPost.layoutParams.height = bmp.height
            }

            roadCode.isVisible = hasRoadCode
            navData.roadCodeBitmap?.let { bmp ->
                roadCode.setImageBitmap(bmp)
                if (bmp.height > 0) {
                    val ratio = bmp.width.toFloat() / bmp.height
                    roadCode.layoutParams.width = (roadCode.layoutParams.height * ratio).toInt()
                }
            }

            navInstruction.isVisible = !hasSignPost
            if (!hasSignPost) {
                navInstruction.text = navData.instructionText
                navInstruction.maxLines = if (hasRoadCode) 1 else 3
            }
        }

        updateTrafficPanel(instruction)
    }

    private fun collectNavigationUiData(instruction: NavigationInstruction, availableWidth: Int): NavigationUiData {
        val instructionText = instruction.nextStreetName?.takeIf {
            it.isNotEmpty()
        } ?: instruction.nextTurnInstruction.orEmpty()

        var hasSameTurnImage = false
        val instructionIcon = getNextTurnImage(instruction, turnImageSize, turnImageSize) { isSame ->
            hasSameTurnImage = isSame
        }

        val trafficDelayInMinutes = navRoute?.let {
            GemUtil.getTrafficEventsDelay(it, true) / 60
        } ?: 0
        val (etaText, rttText, rtdText) = navRoute?.let {
            Triple(it.getEta(), it.getRtt(), it.getRtd())
        } ?: Triple("", "", "")

        val signPostBitmap = getSignpostImage(instruction, availableWidth, signPostImageSize)
        val roadCodeBitmap = if (signPostBitmap == null) {
            getRoadCodeImage(instruction, availableWidth, navigationImageSize)
        } else {
            null
        }

        return NavigationUiData(
            instructionText = instructionText,
            instructionIcon = instructionIcon,
            hasSameTurnImage = hasSameTurnImage,
            instructionDistance = instruction.getDistanceInMeters(),
            etaText = etaText,
            rttText = rttText,
            rttColor = getTrafficColor(trafficDelayInMinutes),
            rtdText = rtdText,
            signPostBitmap = signPostBitmap,
            roadCodeBitmap = roadCodeBitmap,
        )
    }

    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        binding.turnContainer.isVisible = statusMessage.isEmpty()

        if (statusMessage.isNotEmpty()) {
            binding.navInstruction.text = statusMessage
        }
    }

    private fun getStatusMessage(): String {
        when (navigationStatus) {
            ENavigationStatus.WaitingRoute -> {
                val routeStatus = navRoute?.status

                return when (routeStatus) {
                    ERouteStatus.WaitingInternetConnection -> {
                        getString(R.string.waiting_for_internet_connection)
                    }

                    ERouteStatus.Calculating -> {
                        getString(R.string.calculating)
                    }

                    ERouteStatus.Ready -> {
                        getString(R.string.gps_accuracy_not_good_enough)
                    }

                    else -> {
                        getString(R.string.calculating)
                    }
                }
            }
            ENavigationStatus.WaitingGPS -> {
                if (navigationService.isSimulationActive()) {
                    return getString(R.string.calculating)
                }
                return getString(R.string.getting_position)
            }
            else -> {
                // Do nothing for other statuses
            }
        }

        return ""
    }

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            binding.trafficPanel.isVisible = false
            updateFocusViewport()
        } else {
            binding.root.post { updateFocusViewport() }
        }
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
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

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val etaNumber = (this.getTimeDistance(true)?.totalTime ?: 0) + GemUtil.getTrafficEventsDelay(this, true)

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            (this.getTimeDistance(true)?.totalTime ?: 0) + GemUtil.getTrafficEventsDelay(this, true),
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun getTrafficColor(trafficDelayInMinutes: Int): Int {
        val bigTrafficDelayInMinutes = 10
        return when {
            trafficDelayInMinutes == 0 -> Color.rgb(0, 0, 0)
            trafficDelayInMinutes < bigTrafficDelayInMinutes -> Color.rgb(255, 100, 0)
            else -> Color.rgb(235, 0, 0)
        }
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

        // Active turn icon: white fill with black outline; inactive: grey fill and outline.
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

    private fun getSignpostImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        if (!navInstr.hasSignpostInfo()) return null

        val distToNextTurn = navInstr.timeDistanceToNextTurn?.totalDistance ?: 0
        // Suppress the signpost until the driver is within 1 km of the turn to avoid
        // visual clutter on long straight segments.
        if (distToNextTurn > 1000) {
            return null
        }

        return navInstr.signpostDetails?.image?.let { image ->
            GemUtilImages.asBitmap(image, width, height)
        }
    }

    private fun getRoadCodeImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        val roadsInfo = navInstr.nextRoadInformation ?: return null
        if (roadsInfo.isEmpty()) return null
        // Road code shields are typically wider than tall; 2.5× height is a reasonable default
        // aspect ratio when no explicit width constraint is available.
        val resultWidth = if (width == 0) (2.5 * height).toInt() else width
        val image = navInstr.getRoadInfoImage(roadsInfo)
        return GemUtilImages.asBitmap(image, resultWidth, height)
    }

    // Measures the pixel width the given text would occupy in textView, then restores the original
    // text. Used to calculate how much space the distance label can take before layout happens.
    private fun getTextViewWidth(textView: MaterialTextView, text: String): Int {
        val previous = textView.text
        textView.text = text
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(Short.MAX_VALUE.toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val width = textView.measuredWidth
        textView.text = previous
        return width
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

    private fun updateTrafficPanel(instruction: NavigationInstruction) {
        if (!binding.topPanel.isVisible) {
            binding.trafficPanel.isVisible = false
            return
        }

        val route = navRoute ?: run {
            binding.trafficPanel.isVisible = false
            return
        }

        val trafficEvent = getTrafficEvent(instruction, route)
        if (trafficEvent == null) {
            binding.trafficPanel.isVisible = false
            trafficBmp = null
            return
        }

        updateTrafficEventInfo(trafficEvent)
        val bmp = trafficBmp ?: run {
            binding.trafficPanel.isVisible = false
            return
        }

        binding.apply {
            trafficPanel.isVisible = true
            trafficPanel.setBackgroundColor(trafficPanelBackgroundColor)

            if (!sameTrafficImage) {
                trafficImage.setImageBitmap(bmp)
            }

            endOfSectionImage.isVisible = insideTrafficEvent && endOfSectionBmp != null
            if (insideTrafficEvent) {
                endOfSectionBmp?.let { endOfSectionImage.setImageBitmap(it) }
            }

            trafficEventDescription.text = trafficEventDescriptionText

            var prefix = distanceToTrafficPrefixText
            if (prefix.isNotEmpty()) prefix = "$prefix "
            distanceToTrafficPrefix.text = prefix

            distanceToTraffic.text = distanceToTrafficText
            distanceToTrafficUnit.text = distanceToTrafficUnitText

            trafficDelayTime.text = trafficDelayTimeText
            trafficDelayTimeUnit.text = trafficDelayTimeUnitText

            trafficDelayDistance.isVisible = trafficDelayDistanceText.isNotEmpty()
            if (trafficDelayDistanceText.isNotEmpty()) {
                trafficDelayDistance.text = trafficDelayDistanceText
            }

            trafficDelayDistanceUnit.isVisible = trafficDelayDistanceUnitText.isNotEmpty()
            if (trafficDelayDistanceUnitText.isNotEmpty()) {
                trafficDelayDistanceUnit.text = trafficDelayDistanceUnitText
            }
        }
    }

    private fun getTrafficEvent(navInstr: NavigationInstruction, route: Route): RouteTrafficEvent? = SdkCall.execute {
        if (navInstr.navigationStatus != ENavigationStatus.Running) return@execute null
        val trafficEventsList = route.trafficEvents ?: return@execute null

        val remainingTravelDistance = navInstr.remainingTravelTimeDistance?.totalDistance ?: 0

        for (event in trafficEventsList) {
            if (event.delay != 0) {
                val distToDest = event.distanceToDestination
                // Positive value means the event is ahead; negative means we have already passed
                // the start of the event and are potentially inside it.
                distToTrafficEvent = remainingTravelDistance - distToDest

                insideTrafficEvent = false

                if (distToTrafficEvent <= 0) {
                    // How many metres of the event still remain in front of us.
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
        trafficEventDescriptionText = trafficEvent.description ?: ""

        val distance = if (insideTrafficEvent) remainingDistInsideTrafficEvent else distToTrafficEvent
        val distancePair = GemUtil.getDistText(distance, EUnitSystem.Metric, true)
        distanceToTrafficText = distancePair.first
        distanceToTrafficUnitText = distancePair.second

        val theFormat = getString(if (insideTrafficEvent) R.string.out_in_str else R.string.in_str)
        distanceToTrafficPrefixText = String.format(theFormat, "").trim()

        trafficDelayDistanceText = ""
        trafficDelayDistanceUnitText = ""
        trafficDelayTimeText = ""
        trafficDelayTimeUnitText = ""

        if (!trafficEvent.isRoadblock) {
            if (insideTrafficEvent) {
                if (trafficEvent.length > 0) {
                    // Scale the full event delay proportionally to the distance still to travel
                    // through it, so the displayed delay shrinks as the driver progresses.
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
            trafficBmp = newBmp
            sameTrafficImage = false
        } else {
            sameTrafficImage = true
        }
    }

    private fun getTrafficImage(from: RouteTrafficEvent?, width: Int, height: Int): Bitmap? = SdkCall.execute {
        if ((from?.image?.uid ?: 0) == lastTrafficImageId) return@execute null

        val image = from?.image
        if (image != null) {
            lastTrafficImageId = image.uid
        }

        GemUtilImages.asBitmap(image, width, height)
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}
