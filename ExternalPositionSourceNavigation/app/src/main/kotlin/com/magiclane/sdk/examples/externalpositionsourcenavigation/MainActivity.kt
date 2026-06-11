/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.externalpositionsourcenavigation

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import com.magiclane.sdk.examples.externalpositionsourcenavigation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.externalpositionsourcenavigation.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.sensordatasource.ExternalDataSource
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    companion object {
        val positions = arrayOf(
            Pair(48.133931, 11.582914),
            Pair(48.134015, 11.583203),
            Pair(48.134057, 11.583348),
            Pair(48.134085, 11.583499),
            Pair(48.134116, 11.583676),
            Pair(48.134144, 11.583854),
            Pair(48.134166, 11.584010),
            Pair(48.134189, 11.584166),
            Pair(48.134210, 11.584312),
            Pair(48.134231, 11.584458),
            Pair(48.134253, 11.584605),
            Pair(48.134274, 11.584751),
            Pair(48.134295, 11.584897),
            Pair(48.134316, 11.585044),
            Pair(48.134338, 11.585190),
            Pair(48.134361, 11.585335),
            Pair(48.134390, 11.585515),
            Pair(48.134419, 11.585695),
            Pair(48.134443, 11.585846),
            Pair(48.134473, 11.585995),
            Pair(48.134503, 11.586126),
            Pair(48.134467, 11.586266),
            Pair(48.134430, 11.586405),
            Pair(48.134394, 11.586545),
            Pair(48.134357, 11.586684),
            Pair(48.134321, 11.586823),
            Pair(48.134283, 11.586967),
            Pair(48.134297, 11.587086),
            Pair(48.134380, 11.587167),
            Pair(48.134464, 11.587249),
            Pair(48.134563, 11.587345),
            Pair(48.134661, 11.587441),
            Pair(48.134761, 11.587534),
            Pair(48.134861, 11.587626),
            Pair(48.134975, 11.587732),
            Pair(48.135089, 11.587838),
            Pair(48.135204, 11.587943),
            Pair(48.135322, 11.588038),
            Pair(48.135451, 11.588137),
            Pair(48.135581, 11.588231),
            Pair(48.135716, 11.588328),
            Pair(48.135851, 11.588426),
            Pair(48.135972, 11.588513),
            Pair(48.136093, 11.588601),
            Pair(48.136207, 11.588680),
            Pair(48.136322, 11.588759),
            Pair(48.136423, 11.588829),
            Pair(48.136524, 11.588898),
            Pair(48.136615, 11.588962),
            Pair(48.136706, 11.589028),
            Pair(48.136807, 11.589117),
            Pair(48.136905, 11.589215),
            Pair(48.136994, 11.589347),
            Pair(48.137081, 11.589481),
            Pair(48.137164, 11.589608),
            Pair(48.137247, 11.589737),
            Pair(48.137344, 11.589894),
            Pair(48.137444, 11.590049),
            Pair(48.137538, 11.590199),
            Pair(48.137632, 11.590350),
            Pair(48.137730, 11.590508),
            Pair(48.137829, 11.590667),
            Pair(48.137934, 11.590834),
            Pair(48.138038, 11.591002),
            Pair(48.138134, 11.591156),
            Pair(48.138229, 11.591310),
            Pair(48.138316, 11.591454),
            Pair(48.138404, 11.591597),
            Pair(48.138496, 11.591749),
            Pair(48.138589, 11.591900),
            Pair(48.138681, 11.592051),
            Pair(48.138773, 11.592203),
            Pair(48.138867, 11.592351),
            Pair(48.138962, 11.592499),
            Pair(48.139041, 11.592624),
            Pair(48.139126, 11.592740),
            Pair(48.139207, 11.592828),
            Pair(48.139287, 11.592917),
            Pair(48.139374, 11.593012),
            Pair(48.139461, 11.593107),
            Pair(48.139571, 11.593208),
            Pair(48.139685, 11.593301),
            Pair(48.139809, 11.593403),
            Pair(48.139934, 11.593505),
            Pair(48.140077, 11.593604),
            Pair(48.140220, 11.593703),
            Pair(48.140364, 11.593802),
            Pair(48.140514, 11.593876),
            Pair(48.140670, 11.593947),
            Pair(48.140827, 11.594018),
        )
        val destination = Pair(48.17192581, 11.80789822)
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var positionListener: PositionListener

    // Captured once at portrait orientation and reused as the base for all subsequent orientation
    // constraint adjustments, so the portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private var navigationPanelPadding: Int = 0
    private var turnImageSize: Int = 0

    // Long.MAX_VALUE ensures the first real image UID never matches, so it is always rendered.
    private var lastTurnImageId: Long = Long.MAX_VALUE

    private var isNavigationStarted = false
    private var navigationStatus = ENavigationStatus.Running

    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    private var timer: Timer? = null

    private val playingListener = object : SoundPlayingListener() {}
    private val soundPreference = SoundPlayingPreferences()

    /**
     * Receives navigation events from the navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            isNavigationStarted = true

            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route -> mapView.presentRoute(route) }
                    enableGPSButton()
                    mapView.followPosition()
                    EspressoIdlingResource.decrement()
                }
            }

            applyCameraFocus()
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

    // Tracks routing progress: shows a spinner before navigation begins.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            if (!isNavigationStarted) {
                binding.progressBar.isVisible = true
            }
        },
        onCompleted = { _, _ ->
            binding.progressBar.isVisible = false
        },
        onStatusChanged = {
            if (isNavigationStarted) {
                refreshStatusMessage()
            }
        },
        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspressoIdlingResource.increment()

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        SoundUtils.addTTSPlayerInitializationListener(this)

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        navigationPanelPadding = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)

        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        // When the activity launches in landscape, the panels need their horizontal margins set
        // immediately because applyOrientationLayout() removes the END constraint (margin=0).
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
            listOf(binding.topPanel, binding.bottomPanel).forEach { panel ->
                (panel.layoutParams as ConstraintLayout.LayoutParams).apply {
                    marginStart = panelMargin
                    marginEnd = panelMargin
                    panel.layoutParams = this
                }
            }
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

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        clearSdkListeners()
        // Release the SDK.
        GemSdk.release()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
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

        // Delay navigation start until the worldwide road map is fully downloaded and up to date;
        // the callback is cleared immediately after firing to avoid repeat invocations.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                startNavigation()
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

    // Adjusts panel widths and constraints for the current orientation.
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels were
        // GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val bottomVis = binding.bottomPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // In landscape the navigation panel occupies the left 40% so the map area
                // remains visible on the right side of the screen.
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(id, ConstraintSet.END)
                }
            } else {
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
        }.applyTo(rootLayout)

        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis
    }

    // Shifts the camera focus point so the GPS arrow stays in the visible map area.
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // In landscape the navigation panel occupies the left 40%, so shift the camera
            // focus right (0.7) to keep the arrow in the visible map area.
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    // Adjusts the Magic Lane logo position to stay within the visible map area (below the toolbar
    // and beside the navigation panels).
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

        // toolbar.bottom already includes the status bar height because the toolbar has
        // paddingTopWithSystemWindowInsets applied to it.
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: (insets?.top ?: 0)

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            val w = max(width, height)
            val h = min(width, height)

            val left = if (binding.topPanel.isVisible) binding.topPanel.right else insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(toolbarBottom)
            Rect(left, toolbarBottom, right, bottom)
        } else {
            val w = min(width, height)
            val h = max(width, height)

            val left = insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val top = if (binding.topPanel.isVisible) binding.topPanel.bottom else toolbarBottom
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
            updateFocusViewport()
        } else {
            binding.root.post { updateFocusViewport() }
        }
    }

    // Sets up the external position source, feeds simulated GPS points, and starts navigation
    // once a valid position is received.
    private fun startNavigation() = SdkCall.execute {
        val externalDataSource: ExternalDataSource? =
            DataSourceFactory.produceExternal(arrayListOf(EDataType.Position))
        externalDataSource?.start()

        positionListener = PositionListener { position: PositionData ->
            if (position.isValid()) {
                val error = navigationService.startNavigation(
                    Landmark("Poing", destination.first, destination.second),
                    navigationListener,
                    routingProgressListener,
                )
                if (error != GemError.NoError) {
                    val message = SdkCall.runSynced { GemError.getMessage(error, this@MainActivity) } ?: ""
                    runOnUiThread {
                        showDialog(getString(R.string.start_navigation_error, message))
                    }
                }
                PositionService.removeListener(positionListener)
            }
        }

        PositionService.dataSource = externalDataSource
        PositionService.addListener(positionListener)

        var index = 0
        externalDataSource?.let { dataSource ->
            timer = fixedRateTimer("timer", false, 0L, 1000) {
                SdkCall.execute {
                    val externalPosition = PositionData.produce(
                        System.currentTimeMillis(),
                        positions[index].first,
                        positions[index].second,
                        -1.0,
                        positions.getBearing(index),
                        positions.getSpeed(index),
                    )
                    externalPosition?.let { pos -> dataSource.pushData(pos) }
                }
                index++
                if (index == positions.size) index = 0
            }
        }
    }

    private fun enableGPSButton() {
        // Set actions for entering/exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                    setNavigationPanelsVisible(isVisible = false)
                }
                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false
                    if (isNavigationStarted) {
                        setNavigationPanelsVisible(isVisible = true)
                    }
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

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        isNavigationStarted = false
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
        var instrText = ""
        var instrIcon: Bitmap? = null
        var instrDistance = ""
        var etaText = ""
        var rttText = ""
        var rtdText = ""
        var hasSameTurnImage = false

        SdkCall.execute {
            instrText = instruction.nextStreetName?.takeIf { it.isNotEmpty() }
                ?: instruction.nextTurnInstruction.orEmpty()

            instrIcon = getNextTurnImage(instruction, turnImageSize, turnImageSize) { isSame ->
                hasSameTurnImage = isSame
            }
            instrDistance = instruction.getDistanceInMeters()

            navRoute?.apply {
                etaText = getEta()
                rttText = getRtt()
                rtdText = getRtd()
            }
        }

        binding.apply {
            if (!hasSameTurnImage) {
                navIcon.setImageBitmap(instrIcon)
            }
            instructionDistance.text = instrDistance
            navInstruction.text = instrText
            eta.text = etaText
            rtt.text = rttText
            rtd.text = rtdText
        }
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
                return when (navRoute?.status) {
                    ERouteStatus.WaitingInternetConnection -> getString(R.string.waiting_for_internet_connection)
                    ERouteStatus.Calculating -> getString(R.string.calculating)
                    ERouteStatus.Ready -> getString(R.string.gps_accuracy_not_good_enough)
                    else -> getString(R.string.calculating)
                }
            }
            ENavigationStatus.WaitingGPS -> {
                return getString(R.string.getting_position)
            }
            else -> { /* No message for other statuses. */ }
        }
        return ""
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
            button.setOnClickListener { onButtonClick(dialog) }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
    }

    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0
        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).let { pair -> pair.first + " " + pair.second }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
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

        return GemUtilImages.asBitmap(image, width, height, aInner, aOuter, iInner, iOuter)
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

/**
 * Mathematical formula for calculating real distance between 2 coordinates.
 * @return real distance in metres between two geographical points
 */
fun Pair<Double, Double>.getDistanceOnGeoid(to: Pair<Double, Double>): Double {
    val (latitude1, longitude1) = this
    val (latitude2, longitude2) = to
    val lat1 = latitude1 * Math.PI / 180.0
    val lon1 = longitude1 * Math.PI / 180.0
    val lat2 = latitude2 * Math.PI / 180.0
    val lon2 = longitude2 * Math.PI / 180.0

    val r = 6378100.0
    val rho1 = r * cos(lat1)
    val z1 = r * sin(lat1)
    val x1 = rho1 * cos(lon1)
    val y1 = rho1 * sin(lon1)
    val rho2 = r * cos(lat2)
    val z2 = r * sin(lat2)
    val x2 = rho2 * cos(lon2)
    val y2 = rho2 * sin(lon2)

    val dot = (x1 * x2 + y1 * y2 + z1 * z2)
    val cosTheta = dot / (r * r)
    val theta = acos(cosTheta)
    return r * theta
}

/**
 * @return speed equal to the distance between the point at [index] and the previous point,
 * or -1.0 if there is no previous point.
 */
fun Array<Pair<Double, Double>>.getSpeed(index: Int): Double {
    if ((index > 0) && (index < size)) {
        return this[index - 1].getDistanceOnGeoid(this[index])
    }
    return -1.0
}

/**
 * Bearing formula: β = atan2(X, Y) where X and Y are quantities derived from the two coordinates.
 * @return bearing in degrees between the point at [index] and the previous point,
 * or -1.0 if there is no previous point.
 */
fun Array<Pair<Double, Double>>.getBearing(index: Int): Double {
    if ((index > 0) && (index < size)) {
        val x = cos(this[index].first) * sin(this[index].second - this[index - 1].second)
        val y = cos(this[index - 1].first) * sin(this[index].first) -
            sin(this[index - 1].first) * cos(this[index].first) *
            cos(this[index].second - this[index - 1].second)
        return (atan2(x, y) * 180) / Math.PI
    }
    return -1.0
}

object EspressoIdlingResource {
    val espressoIdlingResource =
        CountingIdlingResource("ApplyMapStyleInstrumentedTestsIdlingResource")

    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
