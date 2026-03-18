/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.externalpositionsourcenavigation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
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

    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding

    private lateinit var positionListener: PositionListener

    // Define a navigation service from which we will start navigation
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    private var timer: Timer? = null

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private var turnImageSize: Int = 0

    private var lastTurnImageId: Long = Long.MAX_VALUE

    private var isNavigationStarted = false

    private var navigationStatus = ENavigationStatus.Running

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            isNavigationStarted = true

            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()

                    EspressoIdlingResource.decrement()
                }
            }

            binding.topPanel.isVisible = true
            binding.bottomPanel.isVisible = true
            binding.statusText.isVisible = false
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrIcon: Bitmap? = null
            var instrDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""
            val sameTurnImage = TSameImage()

            SdkCall.execute {
                // Fetch data for the navigation top panel (instruction related info).
                instrText = instr.nextStreetName ?: ""

                if (instrText.isEmpty()) {
                    instrText = instr.nextTurnInstruction ?: ""
                }

                instrIcon = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
                instrDistance = instr.getDistance()

                // Fetch data for the navigation bottom panel (route related info).
                navRoute?.apply {
                    etaText = getEta() // estimated time of arrival
                    rttText = getRtt() // remaining travel time
                    rtdText = getRtd() // remaining travel distance
                }
            }

            // Update the navigation panels info.
            binding.apply {
                if (!sameTurnImage.value) {
                    navIcon.setImageBitmap(instrIcon)
                }

                instructionDistance.text = instrDistance
                navInstruction.text = instrText

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
            if (!isNavigationStarted) {
                binding.progressBar.isVisible = true
                showStatusMessage(getString(R.string.calculating_route))
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
        postOnMain = true
    )

    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        if (statusMessage.isEmpty()) {
            binding.turnContainer.isVisible = true
        } else {
            binding.turnContainer.isVisible = false
            binding.navInstruction.text = statusMessage
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspressoIdlingResource.increment()

        SoundUtils.addTTSPlayerInitializationListener(this)

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected_message))
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                var externalDataSource: ExternalDataSource?

                SdkCall.execute {
                    externalDataSource =
                        DataSourceFactory.produceExternal(arrayListOf(EDataType.Position))
                    externalDataSource?.start()

                    positionListener = PositionListener { position: PositionData ->
                        if (position.isValid()) {
                            navigationService.startNavigation(
                                Landmark("Poing", destination.first, destination.second),
                                navigationListener,
                                routingProgressListener,
                            )

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
                                externalPosition?.let { pos ->
                                    dataSource.pushData(pos)
                                }
                            }
                            index++
                            if (index == positions.size) {
                                index = 0
                            }
                        }
                    }
                }
            }
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        // Deinitialize the SDK.
        GemSdk.release()
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

    private fun showStatusMessage(text: String) {
        binding.statusText.isVisible = true
        binding.statusText.text = text
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

    private fun NavigationInstruction.getDistance(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    /**
     * @return estimated time of arrival
     */
    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    /**
     * @return remaining travel time
     */
    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    /**
     * @return remaining travel distance
     */
    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
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

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
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
                return getString(R.string.getting_position)
            }
            else -> {
                // Do nothing for other statuses
            }
        }

        return ""
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
}

/**
 * Mathematical formula for calculating real distance between 2 coordinates
 * @return real distance between 2 geographical points
 */
fun Pair<Double, Double>.getDistanceOnGeoid(to: Pair<Double, Double>): Double {
    val (latitude1, longitude1) = this
    val (latitude2, longitude2) = to
    // convert degrees to radians
    val lat1 = latitude1 * Math.PI / 180.0
    val lon1 = longitude1 * Math.PI / 180.0
    val lat2 = latitude2 * Math.PI / 180.0
    val lon2 = longitude2 * Math.PI / 180.0

    // radius of earth in metres
    val r = 6378100.0
    // P
    val rho1 = r * cos(lat1)
    val z1 = r * sin(lat1)
    val x1 = rho1 * cos(lon1)
    val y1 = rho1 * sin(lon1)
    // Q
    val rho2 = r * cos(lat2)
    val z2 = r * sin(lat2)
    val x2 = rho2 * cos(lon2)
    val y2 = rho2 * sin(lon2)
    // dot product
    val dot = (x1 * x2 + y1 * y2 + z1 * z2)
    val cosTheta = dot / (r * r)
    val theta = acos(cosTheta)
    // distance in Metres
    return (r * theta)
}

/**
 * @return speed value equal with distance between point at [index] and previous point.
 * If there is no previous point returns -1.0
 */
fun Array<Pair<Double, Double>>.getSpeed(index: Int): Double {
    if ((index > 0) && (index < size)) {
        return this[index - 1].getDistanceOnGeoid(this[index])
    }
    return -1.0
}

/**
 * Calculates bearing between 2 points Formula β = atan2(X,Y) where  X and Y are two quantities
 * that can be calculated based on the given latitude and longitude
 * @return Bearing value between point at [index] and previous point.
 * If there is no previous point returns -1.0
 */
fun Array<Pair<Double, Double>>.getBearing(index: Int): Double {
    if ((index > 0) && (index < size)) {
        val x = cos(this[index].first) * sin(this[index].second - this[index - 1].second)
        val y =
            cos(this[index - 1].first) * sin(this[index].first) - sin(this[index - 1].first) * cos(
                this[index].first,
            ) * cos(this[index].second - this[index - 1].second)
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
