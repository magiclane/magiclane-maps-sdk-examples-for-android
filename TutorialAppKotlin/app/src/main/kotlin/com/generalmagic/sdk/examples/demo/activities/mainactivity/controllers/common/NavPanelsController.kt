/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.Time
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.Util.setPanelBackground
import com.generalmagic.sdk.examples.demo.util.UtilUITexts
import com.generalmagic.sdk.routesandnavigation.AlarmService
import com.generalmagic.sdk.routesandnavigation.ENavigationStatus
import com.generalmagic.sdk.routesandnavigation.NavigationInstruction
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.sensordatasource.PositionData
import com.generalmagic.sdk.util.ConstVals.BIG_TRAFFIC_DELAY_IN_MINUTES
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkUtil
import com.generalmagic.sdk.util.SdkUtil.getDistText
import com.generalmagic.sdk.util.SdkUtil.getSpeedText
import com.generalmagic.sdk.util.UtilGemImages
import kotlin.math.roundToInt

class NavPanelsController(context: Context, attrs: AttributeSet?) :
    ConstraintLayout(context, attrs) {

    private val navInfoPanelFactor = 0.45
    private val navigationPanelPadding =
        context.resources?.getDimension(R.dimen.nav_top_panel_padding)?.toInt() ?: 0

    private val speedPanelBackgroundColor = Color.rgb(225, 55, 55)
    private val nDemoAnimationSteps = 80
    private val nDemoAnimationDuration = 4000
    private var nDemoAnimationCount = 0

    private var allowRefresh = false
    private var demoAnimationRunning = false
    private val navDataProvider = UINavDataProvider()

    private var navInstr: NavigationInstruction? = null
    private var route: Route? = null
    private var alarmService: AlarmService? = null
    private var position: PositionData? = null

    private var laneInfoImage: Bitmap? = null

    lateinit var navigationBottomPanel: ConstraintLayout
    lateinit var topPanelController: NavTopPanelController
    lateinit var navigationDemoText: TextView
    lateinit var navigationSpeedPanel: ConstraintLayout
    lateinit var navigationLanePanel: ConstraintLayout

//    init {
//        // TODO
//            navigationBottomPanel = findViewById(R.id.navigationBottomPanel)
//            topPanelController = findViewById(R.id.topPanelController)
//            navigationDemoText = findViewById(R.id.navigationDemoText)
//            navigationSpeedPanel = findViewById(R.id.navigationSpeedPanel)
//            navigationLanePanel = findViewById(R.id.navigationLanePanel)
//    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val displayMetrics = context.resources.displayMetrics
            val barWidth = (displayMetrics.widthPixels) * navInfoPanelFactor
            navigationBottomPanel.layoutParams.width = barWidth.toInt()
            topPanelController.layoutParams.width = barWidth.toInt()
            navigationDemoText.layoutParams.width = barWidth.toInt()
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            navigationBottomPanel.layoutParams.width = LayoutParams.MATCH_PARENT
            topPanelController.layoutParams.width = LayoutParams.MATCH_PARENT
            navigationDemoText.layoutParams.width = LayoutParams.MATCH_PARENT
        }
        adjustNavInfoTextSize()
        adjustViewsForOrientation(newConfig.orientation)
    }

    fun setIsDemo(value: Boolean) {
        navDataProvider.info.demoText = if (value) "Demo in progress" else null
    }

    fun routeUpdated(value: Route?) {
        route = value

        SdkCall.execute { navDataProvider.updateNavigationInfo(navInstr, value) }
    }

    fun updatePosition(value: PositionData) {
        SdkCall.checkCurrentThread()
        position = value

        navDataProvider.updatePositionInfo(value, navInstr)
    }

    fun updateNavInstruction(value: NavigationInstruction?) {
        if (!allowRefresh) return
        navInstr = value

        val screen = SdkCall.execute { GEMApplication.gemMapScreen() }
        var surfaceWidth = 0
        var surfaceHeight = 0
        SdkCall.execute {
            surfaceWidth = screen?.viewport?.width ?: 0
            surfaceHeight = screen?.viewport?.height ?: 0
        }

        val panelWidth = if (surfaceWidth <= surfaceHeight) {
            surfaceWidth
        } else {
            (surfaceWidth * navInfoPanelFactor).toInt()
        }

        val availableWidthForLaneInfo = if (surfaceWidth <= surfaceHeight) {
            surfaceWidth - 2 * navigationPanelPadding
        } else {
            surfaceWidth - panelWidth - 2 * navigationPanelPadding
        }

        SdkCall.execute {
            navDataProvider.updateNavigationInfo(value, route)

            laneInfoImage = UINavDataProvider.getLaneInfoImage(
                navInstr,
                availableWidthForLaneInfo,
                topPanelController.navigationImageSize
            ).second
        }

        updateAllPanels()
    }

    fun updateAlarmsInfo(value: AlarmService?) {
        alarmService = value
    }

    fun showNavInfo() {
        navigationBottomPanel = findViewById(R.id.navigationBottomPanel)
        topPanelController = findViewById(R.id.topPanelController)
        navigationDemoText = findViewById(R.id.navigationDemoText)
        navigationSpeedPanel = findViewById(R.id.navigationSpeedPanel)
        navigationLanePanel = findViewById(R.id.navigationLanePanel)

        allowRefresh = true
        updateDemoPanel(navDataProvider.info)
        adjustNavInfoTextSize()
    }

    fun hideNavInfo() {
        allowRefresh = false
//        navigationBottomPanel.visibility = View.GONE
//        topPanelController.visibility = View.GONE
//        navigationSpeedPanel.visibility = View.GONE
//        navigationLanePanel.visibility = View.GONE
//        navigationDemoText.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun adjustNavInfoTextSize() {
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val etaText = navigationBottomPanel.findViewById<TextView>(R.id.navMenuETAText)
            val etaTextUnit = navigationBottomPanel.findViewById<TextView>(R.id.navMenuETAUnit)
            val tripDuration =
                navigationBottomPanel.findViewById<TextView>(R.id.navMenuTripDurationText)
            val tripDurationUnit =
                navigationBottomPanel.findViewById<TextView>(R.id.navMenuTripDurationUnit)
            val navDistance = navigationBottomPanel.findViewById<TextView>(R.id.navMenuDistanceText)
            val navDistanceUnit =
                navigationBottomPanel.findViewById<TextView>(R.id.navMenuDistanceUnit)

// 			if (navMenuETAText != null && navMenuETAUnit != null &&
// 				navMenuTripDurationText != null && navMenuTripDurationUnit != null &&
// 				navMenuDistanceText != null && navMenuDistanceUnit != null
// 			) {
// 				return@OnGlobalLayoutListener
// 			}

            val availableWidth = navigationBottomPanel.measuredWidth
            val availableWidthForANavigationTextGroup: Int = availableWidth / 3
            var bigTextFontSize = etaText?.textSize?.roundToInt()?.toFloat() ?: 0.0f
            var smallTextFontSize = etaTextUnit?.textSize?.roundToInt()?.toFloat() ?: 0.0f
            val bigTextFontSizeThreshold = (bigTextFontSize + 2) / 3
            val smallTextFontSizeThreshold = (smallTextFontSize + 2) / 3

            etaText?.text = "12:45"
            etaTextUnit?.text = "AM"

            tripDuration?.text = "20:46"
            tripDurationUnit?.text = "hr"

            navDistance?.text = "10000"
            navDistanceUnit?.text = "km"

            var bFirstGroupFit = Util.doTextGroupFitInsideWidth(
                etaText,
                etaTextUnit,
                availableWidthForANavigationTextGroup
            )
            var bSecondGroupFit = Util.doTextGroupFitInsideWidth(
                tripDuration,
                tripDurationUnit,
                availableWidthForANavigationTextGroup
            )
            var bThirdGroupFit = Util.doTextGroupFitInsideWidth(
                navDistance,
                navDistanceUnit,
                availableWidthForANavigationTextGroup
            )
            var count = 0

            while (!bFirstGroupFit || !bSecondGroupFit || !bThirdGroupFit) {
                bigTextFontSize -= 1
                if ((count % 2) == 0) {
                    smallTextFontSize -= 1
                }

                etaText?.setTextSize(TypedValue.COMPLEX_UNIT_PX, bigTextFontSize)
                etaTextUnit?.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextFontSize)

                tripDuration?.setTextSize(TypedValue.COMPLEX_UNIT_PX, bigTextFontSize)
                tripDurationUnit?.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextFontSize)

                navDistance?.setTextSize(TypedValue.COMPLEX_UNIT_PX, bigTextFontSize)
                navDistanceUnit?.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextFontSize)

                bFirstGroupFit = Util.doTextGroupFitInsideWidth(
                    etaText,
                    etaTextUnit,
                    availableWidthForANavigationTextGroup
                )
                bSecondGroupFit = Util.doTextGroupFitInsideWidth(
                    tripDuration,
                    tripDurationUnit,
                    availableWidthForANavigationTextGroup
                )
                bThirdGroupFit = Util.doTextGroupFitInsideWidth(
                    navDistance,
                    navDistanceUnit,
                    availableWidthForANavigationTextGroup
                )

                ++count

                if (bigTextFontSize <= bigTextFontSizeThreshold ||
                    smallTextFontSize <= smallTextFontSizeThreshold
                ) {
                    break
                }
            }
        }
        navigationBottomPanel.viewTreeObserver?.addOnGlobalLayoutListener(layoutListener)
        navigationBottomPanel.viewTreeObserver?.removeOnGlobalLayoutListener(layoutListener)
    }

    private fun adjustViewsForOrientation(orientation: Int) {
        run {
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    val constraintSet = ConstraintSet()

                    constraintSet.clone(this)
                    constraintSet.connect(
                        R.id.navigationDemoText,
                        ConstraintSet.TOP,
                        R.id.navLayout,
                        ConstraintSet.TOP,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationDemoText,
                        ConstraintSet.END,
                        R.id.navLayout,
                        ConstraintSet.END,
                        10
                    )
                    constraintSet.clear(R.id.navigationDemoText, ConstraintSet.START)
                    constraintSet.applyTo(this)

                    constraintSet.clone(this)
                    constraintSet.connect(
                        R.id.navigationSpeedPanel,
                        ConstraintSet.TOP,
                        R.id.navigationDemoText,
                        ConstraintSet.BOTTOM,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationSpeedPanel,
                        ConstraintSet.END,
                        R.id.navLayout,
                        ConstraintSet.END,
                        0
                    )
                    constraintSet.applyTo(this)

                    constraintSet.clone(this)
                    constraintSet.connect(
                        R.id.navigationLanePanel,
                        ConstraintSet.START,
                        R.id.navigationBottomPanel,
                        ConstraintSet.END,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationLanePanel,
                        ConstraintSet.BOTTOM,
                        R.id.navLayout,
                        ConstraintSet.BOTTOM,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationLanePanel,
                        ConstraintSet.END,
                        R.id.navLayout,
                        ConstraintSet.END,
                        10
                    )
                    constraintSet.applyTo(this)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    val constraintSet = ConstraintSet()

                    constraintSet.clone(this)
                    constraintSet.connect(
                        R.id.navigationDemoText,
                        ConstraintSet.TOP,
                        R.id.topPanelController,
                        ConstraintSet.BOTTOM,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationDemoText,
                        ConstraintSet.END,
                        R.id.navLayout,
                        ConstraintSet.END,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationDemoText,
                        ConstraintSet.START,
                        R.id.navLayout,
                        ConstraintSet.START,
                        10
                    )
                    constraintSet.applyTo(this)

                    constraintSet.clone(this)
                    constraintSet.connect(
                        R.id.navigationSpeedPanel,
                        ConstraintSet.TOP,
                        R.id.navigationDemoText,
                        ConstraintSet.BOTTOM,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationSpeedPanel,
                        ConstraintSet.END,
                        R.id.navLayout,
                        ConstraintSet.END,
                        0
                    )
                    constraintSet.applyTo(this)

                    constraintSet.clone(this)
                    constraintSet.connect(
                        R.id.navigationLanePanel,
                        ConstraintSet.START,
                        R.id.navLayout,
                        ConstraintSet.START,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationLanePanel,
                        ConstraintSet.BOTTOM,
                        R.id.navigationBottomPanel,
                        ConstraintSet.TOP,
                        10
                    )
                    constraintSet.connect(
                        R.id.navigationLanePanel,
                        ConstraintSet.END,
                        R.id.navLayout,
                        ConstraintSet.END,
                        10
                    )
                    constraintSet.applyTo(this)
                }

                else -> return
            }
        }
    }

    private fun updateDemoTextAnimation(duration: Int, steps: Int) {
        if (navigationDemoText.visibility == View.GONE) return
        if (nDemoAnimationCount == steps) {
            nDemoAnimationCount = 0
        }

        nDemoAnimationCount++

        val halfSteps = steps / 2
        val fadeIndex =
            if (nDemoAnimationCount <= halfSteps) {
                255 - nDemoAnimationCount * 255 / halfSteps
            } else {
                nDemoAnimationCount * 255 / halfSteps - 255
            }
        navigationDemoText.setTextColor(Color.argb(fadeIndex, 255, 255, 255))

        val stepDuration = (duration / steps).toLong()

        GEMApplication.postOnMainDelayed({
            if (demoAnimationRunning) {
                updateDemoTextAnimation(duration, steps)
            }
        }, stepDuration)
    }

    /**---------------------------------------------------------------------------------------------
    Update Panels
    ----------------------------------------------------------------------------------------------*/

    private fun updateAllPanels() {
        if (!allowRefresh) return
        /**-----------------------------------------------------------------------------------------
        Update the panels
        ------------------------------------------------------------------------------------------*/
        updateNavigationBottomPanel(navDataProvider.info)

        topPanelController.update(navInstr, route, alarmService)
        updateNavigationTopPanel()
        updateSpeedPanel(navDataProvider.info)
    }

    private fun updateNavigationBottomPanel(navInfo: UINavDataProvider.NavInfo) {
        navigationBottomPanel.visibility = View.VISIBLE

        val color = SdkCall.execute { navInfo.rttColor?.value?.let { Util.getColor(it) } } ?: 0

        findViewById<TextView>(R.id.navMenuETAText).text = navInfo.eta
        findViewById<TextView>(R.id.navMenuETAUnit).text = navInfo.etaUnit
        findViewById<TextView>(R.id.navMenuTripDurationText).text = navInfo.rtt
        findViewById<TextView>(R.id.navMenuTripDurationText).setTextColor(color)
        findViewById<TextView>(R.id.navMenuTripDurationUnit).text = navInfo.rttUnit
        findViewById<TextView>(R.id.navMenuTripDurationUnit).setTextColor(color)
        findViewById<TextView>(R.id.navMenuDistanceText).text = navInfo.rtd
        findViewById<TextView>(R.id.navMenuDistanceUnit).text = navInfo.rtdUnit
    }

    private fun updateNavigationTopPanel() {
        topPanelController.visibility = View.VISIBLE

        val laneInfoImage = this.laneInfoImage
        val laneInformationImage =
            navigationLanePanel.findViewById<ImageView>(R.id.laneInformationImage)
        if (laneInfoImage != null) {
            navigationLanePanel.visibility = View.VISIBLE

            laneInformationImage.apply {
                visibility = View.VISIBLE
                setImageBitmap(laneInfoImage)
            }

            if (laneInfoImage.height > 0) {
                val ratio: Float = (laneInfoImage.width).toFloat() / laneInfoImage.height
                laneInformationImage.layoutParams.width =
                    (laneInformationImage.layoutParams.height * ratio).toInt()
            }
        } else {
            navigationLanePanel.visibility = View.GONE
            laneInformationImage.visibility = View.GONE
        }
    }

    private fun updateSpeedPanel(navInfo: UINavDataProvider.NavInfo) {
        val navCurrentSpeed = navigationSpeedPanel.findViewById<TextView>(R.id.navCurrentSpeed)
        val navCurrentSpeedUnit =
            navigationSpeedPanel.findViewById<TextView>(R.id.navCurrentSpeedUnit)
        val navCurrentSpeedLimit =
            navigationSpeedPanel.findViewById<TextView>(R.id.navCurrentSpeedLimit)
        val navLegalSpeed = navigationSpeedPanel.findViewById<ConstraintLayout>(R.id.navLegalSpeed)

        if (!navInfo.currentSpeed.isNullOrEmpty()) {
            navigationSpeedPanel.visibility = View.VISIBLE


            navCurrentSpeed.text = navInfo.currentSpeed
            navCurrentSpeedUnit.text = navInfo.currentSpeedUnit
            val isOverspeeding = navInfo.isOverspeeding

            if (isOverspeeding) {
                setPanelBackground(this.background, speedPanelBackgroundColor)
                val textColor = ContextCompat.getColor(context, android.R.color.white)

                navCurrentSpeed.setTextColor(textColor)
                navCurrentSpeedUnit.setTextColor(textColor)
            } else {
                setPanelBackground(
                    this.background,
                    ContextCompat.getColor(context, android.R.color.white)
                )

                val textColor = ContextCompat.getColor(context, android.R.color.black)
                navCurrentSpeed.setTextColor(textColor)
                navCurrentSpeedUnit.setTextColor(textColor)
            }
        }

        val limitText = navInfo.currentSpeedLimit

        if (limitText.isNullOrEmpty()) {
            navLegalSpeed.visibility = View.GONE
        } else {
            navLegalSpeed.visibility = View.VISIBLE
            val defaultSize =
                context.resources.getDimension(R.dimen.toolbarTitleTextSize) / resources.displayMetrics.density

            val sp = if (limitText.length >= 3) defaultSize * 0.75f else defaultSize
            navCurrentSpeedLimit.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            navCurrentSpeedLimit.text = limitText
        }
    }

    private fun updateDemoPanel(navInfo: UINavDataProvider.NavInfo) {
        if (navInfo.demoText == null) {
            navigationDemoText.visibility = View.GONE
        } else {
            navigationDemoText.visibility = View.VISIBLE
            navigationDemoText.text = navInfo.demoText

            if (!demoAnimationRunning) {
                demoAnimationRunning = true
                updateDemoTextAnimation(nDemoAnimationDuration, nDemoAnimationSteps)
            }
        }
    }
}

class UINavDataProvider {
    val info = NavInfo()

    /**---------------------------------------------------------------------------------------------
    Public methods
    ----------------------------------------------------------------------------------------------*/

    companion object {
        fun getLaneInfoImage(
            from: NavigationInstruction?, width: Int, height: Int
        ): Pair<Int, Bitmap?> {
            return SdkCall.execute {

                val navInstr = from ?: return@execute Pair(0, null)

                var resultWidth = width
                if (resultWidth == 0) {
                    resultWidth = (2.5 * height).toInt()
                }

                val bkColor = Rgba(0, 0, 0, 255)
                val activeColor = Rgba(255, 255, 255, 255)
                val inactiveColor = Rgba(100, 100, 100, 255)

                val image = navInstr.laneImage

                val resultPair = UtilGemImages.asBitmap(
                    image,
                    resultWidth,
                    height,
                    bkColor,
                    activeColor,
                    inactiveColor
                )

                resultWidth = resultPair.first

                return@execute Pair(resultWidth, resultPair.second)
            } ?: Pair(0, null)
        }
    }

    fun updatePositionInfo(
        value: PositionData,
        navInstr: NavigationInstruction?,
        willReset: Boolean = true
    ) {
        SdkCall.checkCurrentThread()
        if (willReset) resetPositionInfo()

        if (!value.hasSpeed()) return

        var speedLimit = 0.0
        if (navInstr != null && navInstr.navigationStatus == ENavigationStatus.Running) {
            speedLimit = navInstr.currentStreetSpeedLimit
        }

        val speed = value.speed
        info.isOverspeeding = (speedLimit > 0.0) && (speed > speedLimit)

        val speedTextPair = getSpeedText(
            speed, SdkSettings().unitSystem
        )

        info.currentSpeed = speedTextPair.first
        info.currentSpeedUnit = speedTextPair.second

        if (speedLimit > 0.0) {
            info.currentSpeedLimit = getSpeedText(
                speedLimit,
                SdkSettings().unitSystem
            ).first
        }
    }

    fun updateNavigationInfo(
        navInstr: NavigationInstruction?,
        route: Route?,
        willReset: Boolean = true
    ) {
        SdkCall.checkCurrentThread()

        if (willReset) resetNavigationInfo()

        if (navInstr == null || route == null) return

        val getRemainingTravelTime: () -> Int = {
            val totalTime = if (navInstr.navigationStatus == ENavigationStatus.Running) {
                navInstr.remainingTravelTimeDistance?.totalTime ?: 0
            } else {
                route.timeDistance?.totalTime ?: 0
            }

            totalTime + SdkUtil.getTrafficEventsDelay(route, true)
        }

        val getRemainingTravelDistance: () -> Int = {
            if (navInstr.navigationStatus == ENavigationStatus.Running) {
                navInstr.remainingTravelTimeDistance?.totalDistance ?: 0
            } else {
                route.timeDistance?.totalDistance ?: 0
            }
        }

        val arrivalTime = Time()

        arrivalTime.setLocalTime()
        arrivalTime.longValue = arrivalTime.longValue + getRemainingTravelTime() * 1000

        info.eta = String.format("%d:%02d", arrivalTime.hour, arrivalTime.minute)
        info.etaUnit = null

        val pairRemainingTravelText = getDistText(
            getRemainingTravelDistance(),
            SdkSettings().unitSystem,
            true
        )

        info.rtd = pairRemainingTravelText.first
        info.rtdUnit = pairRemainingTravelText.second

        val rttPair = UtilUITexts.getTimeTextWithDays(getRemainingTravelTime())
        info.rtt = rttPair.first
        info.rttUnit = rttPair.second

        val trafficDelayInMinutes = SdkUtil.getTrafficEventsDelay(route, true) / 60

        info.rttColor = when {
            trafficDelayInMinutes == 0 -> {
                // green
                Rgba(0, 170, 0, 255)
            }
            trafficDelayInMinutes < BIG_TRAFFIC_DELAY_IN_MINUTES -> {
                // orange
                Rgba(255, 100, 0, 255)
            }
            else -> {
                // red
                Rgba(235, 0, 0, 255)
            }
        }
    }

    // Private methods

    private fun resetPositionInfo() {
        info.currentSpeed = null
        info.currentSpeedUnit = null
        info.currentSpeedLimit = null
        info.isOverspeeding = false
    }

    private fun resetNavigationInfo() {
        info.eta = null
        info.etaUnit = null
        info.rtd = null
        info.rtdUnit = null
        info.rtt = null
        info.rttUnit = null
        info.rttColor = SdkCall.execute { Rgba(0, 0, 0, 0) }
    }

    data class NavInfo(
        var demoText: String? = null,
        var eta: String? = null,
        var etaUnit: String? = null,
        var rtt: String? = null,
        var rttUnit: String? = null,
        var rttColor: Rgba? = SdkCall.execute { Rgba(0, 0, 0, 0) },
        var rtd: String? = null,
        var rtdUnit: String? = null,
        var currentSpeed: String? = null,
        var currentSpeedUnit: String? = null,
        var currentSpeedLimit: String? = null,
        var isOverspeeding: Boolean = false
    )
}
