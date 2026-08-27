/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.downloadingonboardmapsimulation

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.examples.downloadingonboardmapsimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.downloadingonboardmapsimulation.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
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

    // Avoids re-rendering the same turn arrow every instruction tick.
    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding

    // Captured once at portrait orientation and reused as the base for all subsequent orientation
    // constraint adjustments, so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private val turnImageSize: Int by lazy {
        resources.getDimension(R.dimen.turn_image_size).toInt()
    }

    private var lastTurnImageId: Long = Long.MAX_VALUE

    private val mapName = "Luxembourg"

    private var requiredMapHasBeenDownloaded = false

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    // Content store used to retrieve and download the map.
    private val contentStore = ContentStore()

    // Navigation service used to start and manage the route simulation.
    private val navigationService = NavigationService()

    private var navigationStatus = ENavigationStatus.Running

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    private val checkAuthorizationListener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            showInvalidTokenDialog()
        } else {
            if (!requiredMapHasBeenDownloaded) {
                loadMaps()
            }
        }
    })

    /**
     * Define a navigation listener that will receive notifications from the navigation service.
     * We use onNavigationStarted, onNavigationInstructionUpdated, onDestinationReached,
     * onNotifyStatusChange, onNavigationError, and onNavigationSound.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    navRoute?.let { route -> mapView.presentRoute(route) }
                    enableGPSButton()
                    mapView.followPosition()
                }
            }
            applyCameraFocus()
            setNavigationPanelsVisible(isVisible = true)
            EspressoIdlingResource.decrementNavigationResource()
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

    // Tracks routing progress; drives the progress bar.
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

    private val contentListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
            showStatusMessage(getString(R.string.content_store_started))
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false

            when (errorCode) {
                GemError.NoError -> {
                    SdkCall.execute {
                        val contentListPair =
                            contentStore.getStoreContentList(EContentType.RoadMap)
                                ?: return@execute

                        for (map in contentListPair.first) {
                            val mapName = map.name ?: continue
                            if (mapName.compareTo(this.mapName, true) != 0) continue

                            if (!map.isCompleted()) {
                                val downloadProgressListener = ProgressListener.create(
                                    onStarted = {
                                        onDownloadStarted(map)
                                        showStatusMessage(getString(R.string.downloading_map, mapName))
                                    },
                                    onStatusChanged = { status -> onStatusChanged(status) },
                                    onProgress = { progress -> onProgressUpdated(progress) },
                                    onCompleted = { dlError, _ ->
                                        if (dlError == GemError.NoError) {
                                            showStatusMessage(getString(R.string.map_downloaded, mapName))
                                            onOnboardMapReady()
                                        } else {
                                            EspressoIdlingResource.decrementDownloadingResource()
                                        }
                                    },
                                )
                                map.asyncDownload(
                                    downloadProgressListener,
                                    GemSdk.EDataSavePolicy.UseDefault,
                                    true,
                                )
                            }
                            break
                        }
                    }
                }

                GemError.Cancel -> {
                    showStatusMessage(getString(R.string.content_store_cancelled))
                    EspressoIdlingResource.decrementDownloadingResource()
                }

                else -> {
                    val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                    showDialog(getString(R.string.content_store_error, message))
                    EspressoIdlingResource.decrementDownloadingResource()
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        SoundUtils.addTTSPlayerInitializationListener(this)

        if (EspressoIdlingResource.isDownloadingTest) {
            EspressoIdlingResource.incrementDownloadingResource()
        } else {
            EspressoIdlingResource.incrementNavigationResource()
        }

        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        // When starting directly in landscape, also fix the panel LayoutParams margins so they
        // survive ConstraintSet re-application on subsequent orientation changes.
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
        }

        registerSdkListeners()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        SoundUtils.removeTTSPlayerInitializationListener(this)
        // Release the SDK.
        GemSdk.release()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitSucceeded = onSdkInitSucceeded@{
            val localMaps = contentStore.getLocalContentList(EContentType.RoadMap)
                ?: return@onSdkInitSucceeded

            for (map in localMaps) {
                val mapName = map.name ?: continue
                if (mapName.compareTo(this.mapName, true) == 0) {
                    requiredMapHasBeenDownloaded = map.isCompleted()
                    break
                }
            }

            if (requiredMapHasBeenDownloaded) {
                onOnboardMapReady()
            } else {
                runOnUiThread {
                    if (!Util.isInternetConnected(this)) {
                        showDialog(getString(R.string.internet_required))
                    }
                }
            }
        }

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        // Recompute the logo viewport whenever the surface dimensions change (e.g. rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }

                if (!requiredMapHasBeenDownloaded) {
                    SdkSettings.appAuthorization?.let {
                        SdkCall.execute {
                            SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener)
                        }
                    } ?: run {
                        showInvalidTokenDialog()
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread { showInvalidTokenDialog() }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onSdkInitSucceeded = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Switches between portrait (full-width panels) and landscape (left 40% panels) layouts.
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels
        // were GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val bottomVis = binding.bottomPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility
        val statusVis = binding.statusText.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)
        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // Constrain nav panels to the left 40% so the map stays visible on the right.
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(id, ConstraintSet.END)
                }
                // FAB: move to bottom-right so it sits in the visible map area, not behind the panels.
                // Add the bottom system bar inset so the FAB clears the navigation bar.
                val bottomInset = ViewCompat.getRootWindowInsets(binding.root)
                    ?.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                    )?.bottom ?: 0
                clear(R.id.follow_gps_button, ConstraintSet.BOTTOM)
                connect(
                    R.id.follow_gps_button,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    panelMargin + bottomInset,
                )
            } else {
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
        }.applyTo(rootLayout)

        // Restore visibility state overwritten by ConstraintSet.applyTo().
        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis
        binding.statusText.visibility = statusVis
    }

    // Adjusts the GPS arrow's on-map position to stay inside the visible map area.
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // In landscape the nav panel occupies the left 40% of the screen, so shift the
            // camera focus point right (0.7) to keep the arrow in the visible map area.
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    // Repositions the Magic Lane logo so it never overlaps the navigation panels.
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

            // Exclude the nav panel on the left so the logo stays in the visible map area.
            val left = if (binding.topPanel.isVisible) binding.topPanel.right else insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            // mapContainer and statusText span the full width, so they clip the bottom even in landscape.
            val bottom = bottomBoundary(h - (insets?.bottom ?: 0), top, includeNavPanel = false)
            Rect(left, top, right, bottom)
        } else {
            val w = min(width, height)
            val h = max(width, height)

            val left = insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            // When the top panel is visible its bottom is the correct upper boundary.
            // Otherwise, fall back to the top system-bar inset.
            val top = when {
                binding.topPanel.isVisible -> binding.topPanel.bottom
                else -> insets?.top ?: 0
            }
            val bottom = bottomBoundary(h - (insets?.bottom ?: 0), top, includeNavPanel = true)
            Rect(left, top, right, bottom)
        }
    }

    // Returns the lowest Y coordinate the logo can occupy without overlapping visible bottom panels.
    // includeNavPanel: true in portrait (bottomPanel stacks at the bottom);
    //                  false in landscape (bottomPanel is in the left column, not at the screen bottom).
    private fun bottomBoundary(screenBottom: Int, top: Int, includeNavPanel: Boolean): Int {
        return listOfNotNull(
            binding.bottomPanel.takeIf { includeNavPanel && it.isVisible }?.top,
            binding.mapContainer.takeIf { it.isVisible }?.top,
            binding.statusText.takeIf { it.isVisible }?.top,
        ).minOrNull()?.coerceAtLeast(top) ?: screenBottom.coerceAtLeast(top)
    }

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (isVisible) {
            binding.statusText.isVisible = false
            // Post so the panels have been laid out before we read their dimensions.
            binding.root.post { updateFocusViewport() }
        } else {
            updateFocusViewport()
        }
    }

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                if (message?.isNotEmpty() == true) {
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

    private fun updateNavigationInstruction(instr: NavigationInstruction) {
        val sameImage = TSameImage()
        var instrText = ""
        var instrIcon: Bitmap? = null
        var instrDistance = ""
        var etaText = ""
        var rttText = ""
        var rtdText = ""

        SdkCall.execute {
            instrText = instr.nextStreetName?.takeIf { it.isNotEmpty() } ?: instr.nextTurnInstruction.orEmpty()
            instrIcon = getNextTurnImage(instr, turnImageSize, turnImageSize, sameImage)
            instrDistance = instr.getDistanceInMeters()
            navRoute?.apply {
                etaText = getEta()
                rttText = getRtt()
                rtdText = getRtd()
            }
        }

        binding.apply {
            navInstruction.text = instrText
            if (!sameImage.value) {
                navIcon.setImageBitmap(instrIcon)
            }
            instructionDistance.text = instrDistance
            eta.text = etaText
            rtt.text = rttText
            rtd.text = rtdText
        }
    }

    private fun enableGPSButton() {
        binding.apply {
            gemSurfaceView.mapView?.apply {
                // Panning away from the GPS position hides the nav panels to expose the map.
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                    setNavigationPanelsVisible(isVisible = false)
                }
                // Re-entering GPS follow restores the nav panels if simulation is still running.
                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false
                    val simulationIsActive = SdkCall.execute { navigationService.isSimulationActive() } ?: false
                    if (simulationIsActive) {
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

    private fun onDownloadStarted(map: ContentStoreItem) {
        binding.apply {
            mapContainer.isVisible = true
            // Post so the view has been laid out before we read its dimensions.
            root.post { updateFocusViewport() }

            var flagBitmap: Bitmap? = null
            SdkCall.execute {
                map.countryCodes?.let { codes ->
                    val size = resources.getDimension(R.dimen.icon_size).toInt()
                    flagBitmap = MapDetails().getCountryFlag(codes[0])?.asBitmap(size, size)
                }
            }
            flagIcon.setImageBitmap(flagBitmap)
            countryName.text = SdkCall.execute { map.name }
            mapDescription.text = SdkCall.execute { GemUtil.formatSizeAsText(map.totalSize) }
        }
        EspressoIdlingResource.decrementDownloadingResource()
    }

    private fun onStatusChanged(status: Int) {
        val completed = EContentStoreItemStatus.entries.toTypedArray()[status] == EContentStoreItemStatus.Completed
        binding.downloadProgressBar.isInvisible = completed
    }

    private fun onProgressUpdated(progress: Int) {
        binding.downloadProgressBar.setProgressCompat(progress, true)
    }

    private fun onOnboardMapReady() {
        startSimulation()
        binding.mapContainer.isVisible = false
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Luxembourg", 49.61588784436375, 6.135843869736401),
            Landmark("Mersch", 49.74785494642988, 6.103323786692679),
        )
        val error = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (error != GemError.NoError) {
            val message = GemError.getMessage(error, this)
            runOnUiThread {
                showDialog(getString(R.string.failed_to_start_simulation, message))
            }
        }
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
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

    private fun showInvalidTokenDialog() {
        showDialog(getString(R.string.invalid_token)) { finish() }
    }

    private fun showStatusMessage(text: String) {
        binding.statusText.text = text
        binding.statusText.isVisible = true
        // Post so the view has been laid out before we read its dimensions.
        binding.root.post { updateFocusViewport() }
    }

    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        if (statusMessage.isEmpty()) {
            binding.turnContainer.isVisible = true
        } else {
            binding.turnContainer.isVisible = false
            binding.navInstruction.text = statusMessage
        }
    }

    private fun getStatusMessage(): String {
        if (navigationStatus == ENavigationStatus.WaitingRoute &&
            navRoute?.status == ERouteStatus.WaitingInternetConnection
        ) {
            return getString(R.string.waiting_for_internet_connection)
        }
        return ""
    }

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        sameImage: TSameImage,
    ): Bitmap? {
        if (!navInstr.hasNextTurnInfo()) return null
        if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
            sameImage.value = true
            return null
        }

        val image = navInstr.nextTurnDetails?.abstractGeometryImage
        if (image != null) {
            lastTurnImageId = image.uid
        }

        return GemUtilImages.asBitmap(
            image,
            width,
            height,
            Rgba(255, 255, 255, 255), // active inner
            Rgba(0, 0, 0, 255), // active outer
            Rgba(128, 128, 128, 255), // inactive inner
            Rgba(128, 128, 128, 255), // inactive outer
        )
    }

    private fun loadMaps() = SdkCall.execute {
        contentStore.asyncGetStoreContentList(EContentType.RoadMap, contentListener)
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
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
        ).let { pair -> pair.first + " " + pair.second }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair -> pair.first + " " + pair.second }
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

@VisibleForTesting(VisibleForTesting.PRIVATE)
object EspressoIdlingResource {
    var isDownloadingTest = false
    val navigationIdlingResource = CountingIdlingResource("NavigationIdlingResource")
    val downloadingIdlingResource = CountingIdlingResource("DownloadingIdlingResource")
    fun incrementNavigationResource() = navigationIdlingResource.increment()
    fun incrementDownloadingResource() = downloadingIdlingResource.increment()
    fun decrementNavigationResource() =
        if (!navigationIdlingResource.isIdleNow) navigationIdlingResource.decrement() else Unit
    fun decrementDownloadingResource() =
        if (!downloadingIdlingResource.isIdleNow) downloadingIdlingResource.decrement() else Unit
}
