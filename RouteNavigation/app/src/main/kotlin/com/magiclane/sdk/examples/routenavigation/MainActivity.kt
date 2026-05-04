/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routenavigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.routenavigation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.routenavigation.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private data class NavigationUiData(
        val instructionText: String = "",
        val instructionIcon: Bitmap? = null,
        val hasSameTurnImage: Boolean = false,
        val instructionDistance: String = "",
        val etaText: String = "",
        val rttText: String = "",
        val rtdText: String = "",
    )

    private lateinit var binding: ActivityMainBinding

    // Permission handling
    private var shouldCheckLocationPermissionOnResume = false

    private var turnImageSize: Int = 0

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private var lastTurnImageId: Long = Long.MAX_VALUE

    private var navigationStatus = ENavigationStatus.Running

    private var firstTime = true

    // Modern permissions launcher
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            showDialog(getString(R.string.location_permission_required)) {
                finish()
            }
            return@registerForActivityResult
        }

        onLocationPermissionsGranted(permissions.size)
    }

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
            binding.apply {
                setNavigationPanelsVisible(isVisible = true)
            }
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
            if (firstTime) {
                binding.progressBar.visibility = View.VISIBLE
                firstTime = false
            }
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

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    override fun onResume() {
        super.onResume()
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions()
            } else {
                showDialog(getString(R.string.location_services_required)) {
                    finish()
                }
            }
        }
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                if (checkLocationStatus()) {
                    requestPermissions()
                }
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

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    private fun checkLocationStatus(): Boolean {
        if (isLocationEnabled()) return true

        showLocationDialog(
            message = getString(R.string.location_disabled),
            settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        )
        return false
    }

    private fun showLocationDialog(message: String, settingsIntent: Intent) {
        showBottomSheetDialog(
            title = getString(R.string.location_status),
            message = message,
            buttonText = getString(R.string.open_settings),
            onButtonClick = { dialog ->
                dialog.dismiss()
                startActivity(settingsIntent)
                shouldCheckLocationPermissionOnResume = true
            },
        )
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followCursorButton.visibility = View.VISIBLE
                    setNavigationPanelsVisible(isVisible = false)
                }

                onEnterFollowingPosition = {
                    followCursorButton.visibility = View.GONE

                    val navigationIsActive = SdkCall.execute { navigationService.isNavigationActive() } ?: false
                    if (navigationIsActive) {
                        setNavigationPanelsVisible(isVisible = true)
                    }
                }

                // Set on click action for the GPS button.
                followCursorButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                showDialog(GemError.getMessage(errorCode))
            }
            setNavigationPanelsVisible(isVisible = false)
        }

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
    }

    private fun updateNavigationInstruction(instruction: NavigationInstruction) {
        val navData = SdkCall.execute { collectNavigationUiData(instruction) } ?: return

        binding.apply {
            if (!navData.hasSameTurnImage) {
                navIcon.setImageBitmap(navData.instructionIcon)
            }
            navInstruction.text = navData.instructionText
            instrDistance.text = navData.instructionDistance
            eta.text = navData.etaText
            rtt.text = navData.rttText
            rtd.text = navData.rtdText
        }
    }

    private fun collectNavigationUiData(instruction: NavigationInstruction): NavigationUiData {
        val instructionText =
            instruction.nextStreetName?.takeIf { it.isNotEmpty() } ?: instruction.nextTurnInstruction.orEmpty()

        var hasSameTurnImage = false
        val instructionIcon = getNextTurnImage(instruction, turnImageSize, turnImageSize) { isSame ->
            hasSameTurnImage = isSame
        }

        val (etaText, rttText, rtdText) = navRoute?.let {
            Triple(it.getEta(), it.getRtt(), it.getRtd())
        } ?: Triple("", "", "")

        return NavigationUiData(
            instructionText = instructionText,
            instructionIcon = instructionIcon,
            hasSameTurnImage = hasSameTurnImage,
            instructionDistance = instruction.getDistanceInMeters(),
            etaText = etaText,
            rttText = rttText,
            rtdText = rtdText,
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

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        permissionsLauncher.launch(permissions)
    }

    private fun onLocationPermissionsGranted(grantedPermissionsCount: Int) {
        SdkCall.execute {
            // Keep SDK permission helper in sync with the runtime permission state.
            PermissionsHelper.onRequestPermissionsResult(
                this,
                REQUEST_PERMISSIONS,
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
            waitForValidImprovedPositionAndStartNavigation()
        }
    }

    private fun waitForValidImprovedPositionAndStartNavigation() {
        if (PositionService.improvedPosition?.isValid() == true) {
            startNavigation()
            return
        }

        lateinit var positionListener: PositionListener
        positionListener = PositionListener { position ->
            if (!position.isValid()) return@PositionListener

            PositionService.removeListener(positionListener)
            startNavigation()
        }

        // Wait for first valid improved position before starting navigation.
        PositionService.addListener(positionListener, EDataType.ImprovedPosition)
    }

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
    }

    private fun startNavigation() {
        val destination = Landmark("Paris", 48.8566932, 2.3514616)

        // Cancel any navigation in progress.
        navigationService.cancelNavigation(navigationListener)
        // Start the new navigation.
        val error = navigationService.startNavigation(
            destination,
            navigationListener,
            routingProgressListener,
        )

        if (error != GemError.NoError) {
            runOnUiThread {
                showDialog(getString(R.string.route_navigation_error, GemError.getMessage(error, this)))
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
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
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

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        showBottomSheetDialog(
            title = getString(R.string.error),
            message = text,
            buttonText = null,
            onButtonClick = { dialog ->
                onDismiss?.invoke()
                dialog.dismiss()
            },
        )
    }

    private fun showBottomSheetDialog(
        title: String,
        message: String,
        buttonText: String?,
        onButtonClick: (BottomSheetDialog) -> Unit,
    ) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
            this.message.text = message
            buttonText?.let { button.text = it }
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

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}
