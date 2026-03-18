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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.OverlayItem
import com.magiclane.sdk.d3scene.OverlayService
import com.magiclane.sdk.examples.routesimwithoutmap.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.AlarmListener
import com.magiclane.sdk.routesandnavigation.AlarmService
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
import com.magiclane.sdk.util.EStringIds
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.math.max
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding

    private var lastTurnImageId = Long.MAX_VALUE
    private var lastAlarmImageId = Long.MAX_VALUE
    private var lastTrafficImageId = Long.MAX_VALUE
    private var turnImageSize = 0
    private var topPanelWidth = 0
    private var turnMinWidth = 0
    private var navigationPanelPadding = 0
    private var lanePanelPadding = 0
    private var signPostImageSize = 0
    private var navigationImageSize = 0
    private var currentRoadCodeImageSize = 0
    private var speedLimit = 0.0
    private val speedPanelBackgroundColor = Color.rgb(225, 55, 55)
    private val trafficPanelBackgroundColor = Color.rgb(255, 175, 63)
    private var sameAlarmImage = false
    private var sameTrafficImage = false
    private var alarmBmp: Bitmap? = null
    private var distanceToAlarmText = ""
    private var distanceToAlarmUnitText = ""
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
    private var dpi = 320

    private val navigationService = NavigationService()

    private var navRoute: Route? = null

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

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
                                val defaultTextSize = resources.getDimensionPixelSize(R.dimen.nav_speed_panel_text_size).toFloat()
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
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(

        onNavigationStarted = {
            SdkCall.execute {
                EspressoIdlingResource.increment()
                GemUtilImages.setDpi(dpi)

                PositionService.addListener(positionListener, EDataType.ImprovedPosition)

                alarmService = AlarmService.produce(alarmListener)
                alarmService?.alarmDistance = alarmDistanceMeters

                // the returned array is empty, in absence of map view, but it might change in the future, so we check if the safety overlay is available and add it to the alarm service if it is
                val availableOverlays = OverlayService().getAvailableOverlays(null)?.first
                if (availableOverlays != null) {
                    for (item in availableOverlays) {
                        if (item.uid == ECommonOverlayId.Safety.value) {
                            alarmService?.overlays?.add(item.uid)
                        }
                    }
                }

                endOfSectionBmp = ContextCompat.getDrawable(this, R.drawable.end_of_traffic_section)
                    ?.toBitmap(navigationImageSize, navigationImageSize)

                navRoute = navigationService.getNavigationRoute()
            }

            binding.navigationTopPanel.root.isVisible = true
            binding.bottomPanel.isVisible = true
        },

        onDestinationReached = { _: Landmark ->
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

        onNavigationInstructionUpdated = { instr ->
            var instrDistance = ""
            var instrDistanceUnit = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

            var bDisplayRoadCode = true
            var bDisplayRouteInstruction = true
            var bDisplayedRoadCode = false
            var rttColor = Color.argb(255, 0, 0, 0)

            SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
                GemUtil.getDistText(
                    instr.timeDistanceToNextTurn?.totalDistance ?: 0,
                    EUnitSystem.Metric,
                ).let { pair ->
                    instrDistance = pair.first
                    instrDistanceUnit = pair.second
                }

                speedLimit = if (instr.navigationStatus == ENavigationStatus.Running) {
                    instr.currentStreetSpeedLimit
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

                etaText = instr.getEta(trafficDelay) // estimated time of arrival
                rttText = instr.getRtt(trafficDelay) // remaining travel time
                rtdText = instr.getRtd() // remaining travel distance
            }

            // bottom panel: estimated time of arrival, remaining travel time, remaining travel distance
            binding.apply {
                eta.text = etaText
                rtt.text = rttText
                rtd.text = rtdText

                rtt.setTextColor(rttColor)
            }

            // next turn
            val sameTurnImage = TSameImage()
            val newTurnImage = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
            if (!sameTurnImage.value) {
                binding.navigationTopPanel.turnImage.setImageBitmap(newTurnImage)
            }

            // distance to next turn
            binding.navigationTopPanel.turnDistance.text = instrDistance
            binding.navigationTopPanel.turnDistanceUnit.text = instrDistanceUnit

            val turnDistSize = getTextWidth(binding.navigationTopPanel.turnDistance) +
                               getTextWidth(binding.navigationTopPanel.turnDistanceUnit)
            val turnWidth = max(max(turnDistSize, turnImageSize), turnMinWidth)

            // sign post info
            val availableWidthForMiddlePanel =
                topPanelWidth - turnWidth - 3 * navigationPanelPadding
            val signPostImage =
                getSignpostImage(instr, availableWidthForMiddlePanel, signPostImageSize)

            binding.navigationTopPanel.apply {
                signPost.isVisible = signPostImage != null
                signPostImage?.let {
                    signPost.setImageBitmap(it)
                    signPost.layoutParams.width = it.width
                    signPost.layoutParams.height = it.height

                    bDisplayRoadCode = false
                    bDisplayRouteInstruction = false
                }

                // next road code info
                roadCode.isVisible = bDisplayRoadCode
                if (bDisplayRoadCode) {
                    val roadCodeImage = getRoadCodeImage(
                        instr,
                        availableWidthForMiddlePanel,
                        navigationImageSize,
                    )
                    roadCode.isVisible = roadCodeImage != null
                    roadCodeImage?.let {
                        roadCode.setImageBitmap(it)
                        if (it.height > 0) {
                            val ratio: Float = (it.width).toFloat() / it.height
                            roadCode.layoutParams.width =
                                (roadCode.layoutParams.height * ratio).toInt()
                        }

                        bDisplayedRoadCode = true
                    }
                }

                // next route instruction
                turnInstruction.isVisible = bDisplayRouteInstruction
                if (bDisplayRouteInstruction) {
                    var instrText = ""

                    SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
                        instrText = instr.nextStreetName ?: ""

                        if (instrText.isEmpty()) {
                            instrText = instr.nextTurnInstruction ?: ""
                        }
                    }

                    turnInstruction.isVisible = instrText.isNotEmpty()
                    if (instrText.isNotEmpty()) {
                        turnInstruction.text = instrText
                        turnInstruction.maxLines = if (bDisplayedRoadCode) 1 else 3
                    }
                }
            }

            // lane info / current street name / current road code
            val availableWidthForLaneInfo = topPanelWidth - 2 * navigationPanelPadding
            val laneInfoImage: Bitmap? =
                getLaneInfoImage(instr, availableWidthForLaneInfo, navigationImageSize)

            binding.navigationLanePanel.run {
                laneInfoImage?.let {
                    binding.currentStreetText.isVisible = false

                    if (binding.currentRoadCodeImageContainer.isVisible) {
                        binding.currentRoadCodeImageContainer.isVisible = false
                    }

                    laneInformationImage.setImageBitmap(it)

                    laneInformationImage.layoutParams.width = it.width
                    laneInformationImage.layoutParams.height = it.height

                    root.isVisible = true
                }
            } ?: run {
                binding.navigationLanePanel.root.isVisible = false

                val crtStreetName = SdkCall.execute {
                    instr.currentStreetName
                } ?: ""

                if (crtStreetName.isNotEmpty()) {
                    binding.currentRoadCodeImageContainer.isVisible = false
                    binding.currentStreetText.isVisible = true
                    binding.currentStreetText.text = crtStreetName
                } else {
                    binding.currentStreetText.isVisible = false

                    val currentRoadCodeImg = getRoadCodeImage(
                        instr,
                        availableWidthForMiddlePanel,
                        currentRoadCodeImageSize,
                        false,
                    )

                    currentRoadCodeImg?.let {
                        binding.currentRoadCodeImage.setImageBitmap(it)
                        binding.currentRoadCodeImageContainer.isVisible = true
                    } ?: run {
                        binding.currentRoadCodeImageContainer.isVisible = false
                    }
                }
            }

            // safety alarm
            updateAlarmsInfo()
            binding.navigationTopPanel.apply {
                alarmPanel.isVisible = alarmBmp != null
                alarmBmp?.let {
                    if (!sameAlarmImage) {
                        alarmIcon.setImageBitmap(it)

                        if (it.height > 0) {
                            val ratio = it.width.toFloat() / it.height
                            alarmIcon.layoutParams.width =
                                (alarmIcon.layoutParams.height * ratio).toInt()
                        }
                    }

                    val shouldShowDistanceToAlarm =
                        distanceToAlarmText.isNotEmpty() && distanceToAlarmUnitText.isNotEmpty()
                    distanceToAlarm.isVisible = shouldShowDistanceToAlarm
                    distanceToAlarmUnit.isVisible = shouldShowDistanceToAlarm
                    if (shouldShowDistanceToAlarm) {
                        distanceToAlarm.text = distanceToAlarmText
                        distanceToAlarm.setTextColor(Color.BLACK)
                        distanceToAlarmUnit.text = distanceToAlarmUnitText
                        distanceToAlarm.setTextColor(Color.BLACK)
                    }
                }
            }

            // traffic event
            binding.navigationTopPanel.apply {
                trafficPanel.isVisible = navRoute != null
                navRoute?.let { route ->
                    val trafficEvent = getTrafficEvent(instr, route)
                    trafficEvent?.let { event ->
                        updateTrafficEventInfo(event)
                        trafficBmp?.let { bmp ->
                            trafficPanel.isVisible = true

                            trafficPanel.background = ContextCompat.getDrawable(
                                this@MainActivity,
                                if (alarmBmp != null) R.drawable.white_button else R.drawable.bottom_rounded_white_button,
                            )

                            val layoutParams: FrameLayout.LayoutParams =
                                trafficImage.layoutParams as FrameLayout.LayoutParams
                            val margin: Int = navigationPanelPadding
                            val top: Int = navigationPanelPadding - getSizeInPixels(1)

                            layoutParams.setMargins(
                                margin,
                                if (alarmBmp != null) margin else top,
                                margin,
                                margin,
                            )
                            endOfSectionImage.layoutParams = layoutParams

                            setBackgroundColor(trafficPanel.background, trafficPanelBackgroundColor)

                            if (!sameTrafficImage) {
                                trafficImage.setImageBitmap(bmp)
                            }

                            endOfSectionImage.isVisible =
                                insideTrafficEvent && endOfSectionBmp != null
                            if (insideTrafficEvent) {
                                endOfSectionBmp?.let {
                                    endOfSectionImage.setImageBitmap(it)
                                }
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

                            trafficDelayDistanceUnit.isVisible =
                                trafficDelayDistanceUnitText.isNotEmpty()
                            if (trafficDelayDistanceUnitText.isNotEmpty()) {
                                trafficDelayDistanceUnit.text = trafficDelayDistanceUnitText
                            }
                        } ?: run {
                            trafficPanel.isVisible = false
                        }
                    } ?: run {
                        trafficPanel.isVisible = false
                        trafficBmp = null
                    }
                }
            }
            EspressoIdlingResource.decrement()
        },

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
            binding.progressBar.isVisible = true
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false

            if (errorCode != GemError.NoError) {
                showDialog(GemError.getMessage(errorCode))
            }
        },

        postOnMain = true,
    )

    private var alarmService: AlarmService? = null
    private val alarmDistanceMeters = 500.0
    private var alarmListener = AlarmListener.create()

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        bSameImage: TSameImage,
    ): Bitmap? {
        return SdkCall.execute {
            if (!navInstr.hasNextTurnInfo()) return@execute null
            if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
                bSameImage.value = true
                return@execute null
            }

            val image = navInstr.nextTurnDetails?.abstractGeometryImage
            if (image != null) {
                lastTurnImageId = image.uid
            }

            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            GemUtilImages.asBitmap(
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

    private fun getSignpostImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        var result: Bitmap? = null
        SdkCall.execute {
            if (navInstr.hasSignpostInfo()) {
                navInstr.signpostDetails?.image?.let {
                    result = GemUtilImages.asBitmap(it, width, height)
                }
            }
        }
        return result
    }

    private fun getRoadCodeImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        nextRoadCode: Boolean = true,
    ): Bitmap? {
        return SdkCall.execute {
            val roadsInfo = if (nextRoadCode) {
                navInstr.nextRoadInformation ?: return@execute null
            } else {
                navInstr.currentRoadInformation ?: return@execute null
            }

            if (roadsInfo.isNotEmpty()) {
                var resultWidth = width
                if (resultWidth == 0) {
                    resultWidth = (2.5 * height).toInt()
                }

                val image = navInstr.getRoadInfoImage(roadsInfo)

                GemUtilImages.asBitmap(image, resultWidth, height)
            } else {
                null
            }
        }
    }

    private fun getLaneInfoImage(navInstr: NavigationInstruction, width: Int, height: Int): Bitmap? {
        return SdkCall.execute {
            var resultWidth = width
            if (resultWidth == 0) {
                resultWidth = (2.5 * height).toInt()
            }

            val bkColor = Rgba(0, 0, 0, 255)
            val activeColor = Rgba(255, 255, 255, 255)
            val inactiveColor = Rgba(100, 100, 100, 255)

            val image = navInstr.laneImage

            val bmp = GemUtilImages.asBitmap(
                image,
                resultWidth,
                height,
                bkColor,
                activeColor,
                inactiveColor,
            )

            return@execute bmp
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        EspressoIdlingResource.increment()

        SoundUtils.addTTSPlayerInitializationListener(this)

        supportActionBar?.hide()

        dpi = resources.displayMetrics.densityDpi

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        turnMinWidth = resources.getDimension(R.dimen.nav_top_panel_turn_min_width).toInt()
        navigationPanelPadding = resources.getDimension(R.dimen.nav_top_panel_padding).toInt()
        lanePanelPadding = resources.getDimension(R.dimen.route_status_text_lateral_padding).toInt()
        signPostImageSize = resources.getDimension(R.dimen.sign_post_image_size).toInt()
        navigationImageSize = resources.getDimension(R.dimen.navigation_image_size).toInt()
        currentRoadCodeImageSize =
            resources.getDimension(R.dimen.nav_top_panel_road_img_size).toInt()

        topPanelWidth = resources.displayMetrics.widthPixels

        // Set status bar color to black and icons to white
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            startSimulation()
            SdkSettings.onMapDataReady = {}
        }

        SdkSettings.onApiTokenRejected = {
            showDialog("The provided token is invalid. " +
                             "If you don't have another token, " +
                             "check the magiclane.com website, sign up / in and generate one. Then input it in the AndroidManifest.xml file.")
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (GemSdk.initSdkWithDefaults(this) != GemError.NoError) {
            // The SDK initialization was not completed.
            finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.navigationTopPanel.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, v.paddingBottom)
            insets
        }

        if (!Util.isInternetConnected(this)) {
            showDialog("You must be connected to the internet!")
        }
        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        SoundUtils.removeTTSPlayerInitializationListener(this)

        // Deinitialize the SDK.
        GemSdk.release()
    }

    @SuppressLint("DefaultLocale")
    private fun NavigationInstruction.getEta(trafficDelay: Int): String {
        val etaNumber = (remainingTravelTimeDistance?.totalTime ?: 0) + trafficDelay

        val time = Time()
        time.setLocalTime()
        time.longValue = time.longValue + etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    private fun NavigationInstruction.getRtt(trafficDelay: Int): String {
        return GemUtil.getTimeText((remainingTravelTimeDistance?.totalTime ?: 0) + trafficDelay)
            .let { pair ->
                pair.first + " " + pair.second
            }
    }

    private fun NavigationInstruction.getRtd(): String {
        return GemUtil.getDistText(
            remainingTravelTimeDistance?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun startSimulation() = SdkCall.execute {
        // val waypoints = arrayListOf(Landmark("Amsterdam", 52.3585050, 4.8803423), Landmark("Paris", 48.8566932, 2.3514616))
        // val waypoints = arrayListOf(Landmark("Brasov", 45.65139, 25.60528), Landmark("Predeal", 45.50187, 25.57408))
        // val waypoints = arrayListOf(Landmark("General Magic", 45.65135, 25.60505), Landmark("Codlea", 45.69248, 25.44899))
        // val waypoints = arrayListOf(Landmark("Bulevardul Saturn", 45.64717, 25.62943), Landmark("Calea Bucuresti", 45.63497, 25.63531))
        val waypoints = arrayListOf(
            Landmark("London", 51.50732, -0.12765),
            Landmark("Paris", 48.85669, 2.35146),
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    @SuppressLint("InflateParams")
    private fun showDialog(text: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }

    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }

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

    private fun updateAlarmsInfo() = SdkCall.execute {
        sameAlarmImage = false
        distanceToAlarmText = ""
        distanceToAlarmUnitText = ""

        alarmService?.let {
            val markersList = it.overlayItemAlarms
            if ((markersList != null) && (markersList.size > 0)) {
                val distance = markersList.getDistance(0)
                if (distance < it.alarmDistance) {
                    val sameImage = TSameImage()
                    val textsPair = GemUtil.getDistText(distance.toInt(), EUnitSystem.Metric, true)
                    val safetyAlarmPair = getSafetyCameraAlarmImage(
                        markersList.getItem(0),
                        navigationImageSize,
                        sameImage,
                    )

                    distanceToAlarmText = textsPair.first
                    distanceToAlarmUnitText = textsPair.second
                    sameAlarmImage = sameImage.value

                    if (!sameAlarmImage) {
                        alarmBmp = safetyAlarmPair.second

                        if (alarmBmp != null) {
                            val warning = String.format(
                                GemUtil.getTTSString(EStringIds.eStrCaution),
                                GemUtil.getTTSString(EStringIds.eStrSpeedCamera),
                            )
                            if (warning.isNotEmpty()) {
                                SoundPlayingService.playText(
                                    warning,
                                    playingListener,
                                    soundPreference,
                                )
                            }
                        }
                    }

                    return@execute
                }
            }
        }

        alarmBmp = null
    }

    private fun getSafetyCameraAlarmImage(from: OverlayItem?, height: Int, sameImage: TSameImage): Pair<Int, Bitmap?> =
        SdkCall.execute {
            val marker = from ?: return@execute Pair(0, null)
            if ((marker.image?.uid ?: 0) == lastAlarmImageId) {
                sameImage.value = true
                return@execute Pair(0, null)
            }

            val aspectRatio = getImageAspectRatio(marker)
            val actualWidth = (aspectRatio * height).toInt()

            val image = marker.image
            if (image != null) {
                lastAlarmImageId = image.uid
            }

            return@execute Pair(actualWidth, GemUtilImages.asBitmap(image, actualWidth, height))
        } ?: Pair(0, null)

    private fun getImageAspectRatio(marker: OverlayItem?): Float {
        val image = marker?.image ?: return 1.0f
        var fAspectRatio = 1.0f

        val size = image.size
        if (size != null && size.height != 0) {
            fAspectRatio = (size.width.toFloat() / size.height.toFloat())
        }

        return fAspectRatio
    }

    private fun getTrafficImage(from: RouteTrafficEvent?, width: Int, height: Int, sameImage: TSameImage): Bitmap? =
        SdkCall.execute {
            if ((from?.image?.uid ?: 0) == lastTrafficImageId) {
                sameImage.value = true
                return@execute null
            }

            val image = from?.image
            if (image != null) {
                lastTrafficImageId = image.uid
            }

            GemUtilImages.asBitmap(image, width, height)
        }

    private fun getTrafficEvent(navInstr: NavigationInstruction, route: Route): RouteTrafficEvent? = SdkCall.execute {
        if (navInstr.navigationStatus != ENavigationStatus.Running) return@execute null
        val trafficEventsList = route.trafficEvents ?: return@execute null

        val remainingTravelDistance = navInstr.remainingTravelTimeDistance?.totalDistance ?: 0

        // pick current traffic event
        for (event in trafficEventsList) {
            if (event.delay != 0) {
                val distToDest = event.distanceToDestination
                distToTrafficEvent = remainingTravelDistance - distToDest

                insideTrafficEvent = false

                if (distToTrafficEvent <= 0) {
                    remainingDistInsideTrafficEvent =
                        event.length - (distToDest - remainingTravelDistance)

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

        val distance = if (insideTrafficEvent) {
            remainingDistInsideTrafficEvent
        } else {
            distToTrafficEvent
        }

        val distanceToTrafficPair = GemUtil.getDistText(distance, EUnitSystem.Metric, true)

        distanceToTrafficText = distanceToTrafficPair.first
        distanceToTrafficUnitText = distanceToTrafficPair.second

        val theFormat = getString(if (insideTrafficEvent) R.string.out_in_str else R.string.in_str)

        distanceToTrafficPrefixText = String.format(theFormat, "").trim()

        trafficDelayDistanceText = ""
        trafficDelayDistanceUnitText = ""
        trafficDelayTimeText = ""
        trafficDelayTimeUnitText = ""

        if (!trafficEvent.isRoadblock) {
            if (insideTrafficEvent) {
                if (trafficEvent.length > 0) {
                    val nRemainingTimeInsideTrafficEvent =
                        (trafficEvent.delay * remainingDistInsideTrafficEvent) / trafficEvent.length
                    val trafficDelayTextPair = GemUtil.getTimeText(nRemainingTimeInsideTrafficEvent)

                    trafficDelayTimeText = trafficDelayTextPair.first
                    trafficDelayTimeUnitText = trafficDelayTextPair.second
                }
            } else {
                val trafficDistTextPair =
                    GemUtil.getDistText(trafficEvent.length, SdkSettings.unitSystem, true)

                trafficDelayDistanceText = trafficDistTextPair.first
                trafficDelayDistanceUnitText = trafficDistTextPair.second

                val trafficDelayTextPair = GemUtil.getTimeText(trafficEvent.delay)

                trafficDelayTimeText = String.format("+%s", trafficDelayTextPair.first)
                trafficDelayTimeUnitText = trafficDelayTextPair.second
            }
        }

        val sameImage = TSameImage()
        val newTrafficBmp =
            getTrafficImage(trafficEvent, navigationImageSize, navigationImageSize, sameImage)
        if (!sameImage.value) {
            trafficBmp = newTrafficBmp
            sameTrafficImage = false
        } else {
            sameTrafficImage = true
        }
    }

    private fun getSizeInPixels(dpi: Int): Int {
        val metrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpi.toFloat(), metrics)
            .toInt()
    }

    fun getTextWidth(textView: TextView, maxWidth: Int = Short.MAX_VALUE.toInt()): Int
    {
        val widthMeasureSpec: Int =
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
        val heightMeasureSpec: Int =
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
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
