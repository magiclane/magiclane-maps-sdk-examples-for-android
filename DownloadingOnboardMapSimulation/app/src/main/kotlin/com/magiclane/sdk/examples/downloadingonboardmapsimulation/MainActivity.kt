/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.downloadingonboardmapsimulation

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
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
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
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
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {
    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding

    private val turnImageSize: Int by lazy {
        resources.getDimension(R.dimen.turn_image_size).toInt()
    }

    private var lastTurnImageId: Long = Long.MAX_VALUE

    private val mapName = "Luxembourg"

    private var requiredMapHasBeenDownloaded = false

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    // Define a content store that will deliver us the map.
    private val contentStore = ContentStore()

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private var navigationStatus = ENavigationStatus.Running

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    private val checkAuthorizationListener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            showInvalidTokenDialog()
        }
        else {
            if (!requiredMapHasBeenDownloaded) {
                loadMaps()
            }
        }
    })

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }

            binding.topPanel.isVisible = true
            binding.bottomPanel.isVisible = true
            binding.statusText.isVisible = false

            EspressoIdlingResource.decrementNavigationResource()
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrIcon: Bitmap? = null
            val sameTurnImage = TSameImage()
            var instrDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

            SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
                instrText = instr.nextStreetName ?: ""

                if (instrText.isEmpty()) {
                    instrText = instr.nextTurnInstruction ?: ""
                }

                instrIcon = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
                instrDistance = instr.getDistanceInMeters()

                // Fetch data for the navigation bottom panel (route related info).
                navRoute?.apply {
                    etaText = getEta() // estimated time of arrival
                    rttText = getRtt() // remaining travel time
                    rtdText = getRtd() // remaining travel distance
                }
            }

            // Update the navigation panels info.
            binding.apply {
                navInstruction.text = instrText
                if (!sameTurnImage.value) {
                    navIcon.setImageBitmap(instrIcon)
                }
                instructionDistance.text = instrDistance

                eta.text = etaText
                rtt.text = rttText
                rtd.text = rtdText
            }
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
        canPlayNavigationSound = true
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
            showStatusMessage("Routing process started.")
        },
        onCompleted = { _, _ ->
            binding.progressBar.isVisible = false
            showStatusMessage("Routing process completed.")
        },
        postOnMain = true,
    )

    private val contentListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
            showStatusMessage("Started content store service.")
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false
            showStatusMessage("Content store service completed with error code: $errorCode")

            when (errorCode) {
                GemError.NoError -> {
                    // No error encountered, we can handle the results.
                    SdkCall.execute {
                        // Get the list of maps that was retrieved in the content store.
                        val contentListPair =
                            contentStore.getStoreContentList(
                                EContentType.RoadMap,
                            ) ?: return@execute

                        for (map in contentListPair.first) {
                            val mapName = map.name ?: continue
                            if (mapName.compareTo(this.mapName, true) != 0) {
                                continue
                            }

                            if (!map.isCompleted()) {
                                // Define a listener to the progress of the map download action.
                                val downloadProgressListener = ProgressListener.create(
                                    onStarted = {
                                        onDownloadStarted(map)
                                        showStatusMessage("Started downloading $mapName.")
                                    },
                                    onStatusChanged = { status ->
                                        onStatusChanged(status)
                                    },
                                    onProgress = { progress ->
                                        onProgressUpdated(progress)
                                    },
                                    onCompleted = { errorCode, _ ->
                                        if (errorCode == GemError.NoError) {
                                            showStatusMessage("$mapName was downloaded.")
                                            onOnboardMapReady()
                                        } else {
                                            EspressoIdlingResource.decrementDownloadingResource()
                                        }
                                    },
                                )

                                // Start downloading the first map item.
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
                    showStatusMessage(
                        "Content store service completed with error code: $errorCode",
                    )
                    EspressoIdlingResource.decrementDownloadingResource()
                }

                else -> {
                    // There was a problem at retrieving the content store items.
                    showStatusMessage(
                        "Content store service completed with error code: $errorCode",
                    )
                    showDialog("Content store service error: ${GemError.getMessage(errorCode)}")
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

        binding.gemSurfaceView.onSdkInitSucceeded = onSdkInitSucceeded@{
            val localMaps = contentStore.getLocalContentList(EContentType.RoadMap) ?: return@onSdkInitSucceeded

            for (map in localMaps) {
                val mapName = map.name ?: continue
                if (mapName.compareTo(this.mapName, true) == 0) {
                    requiredMapHasBeenDownloaded = map.isCompleted()
                    break
                }
            }

            if (requiredMapHasBeenDownloaded) {
                onOnboardMapReady()
            }
            else {
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

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

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
            showInvalidTokenDialog()
        }
    }

    private fun onDownloadStarted(map: ContentStoreItem) {
        binding.apply {
            mapContainer.isVisible = true

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
        binding.downloadedIcon.isVisible =
            EContentStoreItemStatus.entries.toTypedArray()[status] == EContentStoreItemStatus.Completed
        binding.downloadProgressBar.isInvisible =
            EContentStoreItemStatus.entries.toTypedArray()[status] == EContentStoreItemStatus.Completed
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

        navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
        )
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
        showDialog(
            getString(R.string.invalid_token),
        ) {
            finish()
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
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format(Locale.getDefault(), "%d:%02d", time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
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

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun enableGPSButton() { // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followCursorButton.isVisible = true
                }
                onEnterFollowingPosition = {
                    followCursorButton.isVisible = false
                }
                // Set on click action for the GPS button.
                followCursorButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                showDialog(GemError.getMessage(errorCode))
            }

            binding.apply {
                binding.topPanel.isVisible = false
                binding.bottomPanel.isVisible = false
            }
        }

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
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

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        sameImage: TSameImage
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

    private fun getStatusMessage(): String {
        when (navigationStatus) {
            ENavigationStatus.WaitingRoute -> {
                if (navRoute?.status == ERouteStatus.WaitingInternetConnection) {
                    return getString(R.string.waiting_for_internet_connection)
                }
            }
            else -> {
                // Do nothing for other statuses
            }
        }

        return ""
    }

    private fun loadMaps() = SdkCall.execute {
        // Call to the content store to asynchronously retrieve the list of maps.
        contentStore.asyncGetStoreContentList(EContentType.RoadMap, contentListener)
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
