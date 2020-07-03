// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.controllers.common

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.models.Position
import com.generalmagic.gemsdk.models.Time
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.R
import com.generalmagic.gemsdkdemo.util.StaticsHolder
import com.generalmagic.gemsdkdemo.util.Util
import com.generalmagic.gemsdkdemo.util.Util.Companion.setPanelBackground
import com.generalmagic.gemsdkdemo.util.UtilUITexts
import com.generalmagic.gemsdkdemo.util.Utils.Companion.getDistText
import kotlinx.android.synthetic.main.nav_bottom_panel.view.*
import kotlinx.android.synthetic.main.nav_lane_panel.view.*
import kotlinx.android.synthetic.main.nav_layout.view.*
import kotlinx.android.synthetic.main.nav_speed_limit_sign.view.*
import kotlinx.android.synthetic.main.nav_speed_panel.view.*
import kotlinx.android.synthetic.main.nav_top_panel.view.*
import kotlin.math.roundToInt


class NavPanelsController(context: Context?, attrs: AttributeSet?) :
	ConstraintLayout(context, attrs) {

	private val navInfoPanelFactor = 0.45
	private val navigationPanelPadding =
		context?.resources?.getDimension(R.dimen.nav_top_panel_padding)?.toInt() ?: 0

	private val trafficPanelBackgroundColor = Color.rgb(255, 175, 63)
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
	private var position: Position? = null

	private var laneInfoImage: Bitmap? = null

	// ---------------------------------------------------------------------------------------------

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

		GEMSdkCall.execute {
			navDataProvider.updateNavigationInfo(navInstr, value)
		}

		updateAllPanels()
	}

	fun updatePosition(value: Position) {
		position = value

		GEMSdkCall.execute { navDataProvider.updatePositionInfo(value, navInstr) }

		updateAllPanels()
	}

	fun updateNavInstruction(value: NavigationInstruction?) {
		navInstr = value

		val screen = StaticsHolder.gemMapScreen()
		var surfaceWidth = 0
		var surfaceHeight = 0
		GEMSdkCall.execute {
			surfaceWidth = screen?.getViewPort()?.width() ?: 0
			surfaceHeight = screen?.getViewPort()?.height() ?: 0
		}

		val panelWidth = if (surfaceWidth <= surfaceHeight)
			surfaceWidth
		else
			(surfaceWidth * navInfoPanelFactor).toInt()

		val availableWidthForLaneInfo = if (surfaceWidth <= surfaceHeight)
			surfaceWidth - 2 * navigationPanelPadding
		else
			surfaceWidth - panelWidth - 2 * navigationPanelPadding

		GEMSdkCall.execute {
			navDataProvider.updateNavigationInfo(value, route)

			laneInfoImage = UINavDataProvider.getLaneInfoImage(
				navInstr, availableWidthForLaneInfo, topPanelController.navigationImageSize
			).second
		}

		updateAllPanels()
	}

	fun updateAlarmsInfo(value: AlarmService?) {
		alarmService = value

		updateAllPanels()
	}

	// ---------------------------------------------------------------------------------------------

	fun showNavInfo() {
		allowRefresh = true
		adjustNavInfoTextSize()
	}

	fun hideNavInfo() {
		allowRefresh = false
		navigationBottomPanel.visibility = View.GONE
		topPanelController.visibility = View.GONE
		navigationSpeedPanel.visibility = View.GONE
		navigationLanePanel.visibility = View.GONE
		navigationDemoText.visibility = View.GONE
	}

	// ---------------------------------------------------------------------------------------------

	private fun adjustNavInfoTextSize() {
		val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
			val etaText = navigationBottomPanel?.navMenuETAText
			val etaTextUnit = navigationBottomPanel?.navMenuETAUnit
			val tripDuration = navigationBottomPanel?.navMenuTripDurationText
			val tripDurationUnit = navigationBottomPanel?.navMenuTripDurationUnit
			val navDistance = navigationBottomPanel?.navMenuDistanceText
			val navDistanceUnit = navigationBottomPanel?.navMenuDistanceUnit

//			if (navMenuETAText != null && navMenuETAUnit != null &&
//				navMenuTripDurationText != null && navMenuTripDurationUnit != null &&
//				navMenuDistanceText != null && navMenuDistanceUnit != null
//			) {
//				return@OnGlobalLayoutListener
//			}

			val navMenuPadding = etaText?.marginStart ?: 0
			val availableWidth = navigationBottomPanel?.measuredWidth?:0 - 2 * navMenuPadding
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
				etaText, etaTextUnit, availableWidthForANavigationTextGroup
			)
			var bSecondGroupFit = Util.doTextGroupFitInsideWidth(
				tripDuration, tripDurationUnit, availableWidthForANavigationTextGroup
			)
			var bThirdGroupFit = Util.doTextGroupFitInsideWidth(
				navDistance, navDistanceUnit, availableWidthForANavigationTextGroup
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
					etaText, etaTextUnit, availableWidthForANavigationTextGroup
				)
				bSecondGroupFit = Util.doTextGroupFitInsideWidth(
					tripDuration, tripDurationUnit, availableWidthForANavigationTextGroup
				)
				bThirdGroupFit = Util.doTextGroupFitInsideWidth(
					navDistance, navDistanceUnit, availableWidthForANavigationTextGroup
				)

				++count

				if (bigTextFontSize <= bigTextFontSizeThreshold
					|| smallTextFontSize <= smallTextFontSizeThreshold
				) {
					break
				}
			}
		}
		navigationBottomPanel?.viewTreeObserver?.addOnGlobalLayoutListener(layoutListener)
		navigationBottomPanel?.viewTreeObserver?.removeOnGlobalLayoutListener(layoutListener)
	}

	// ---------------------------------------------------------------------------------------------

	private fun adjustViewsForOrientation(orientation: Int) {
		run {
			when (orientation) {
				Configuration.ORIENTATION_LANDSCAPE -> {
					val constraintSet = ConstraintSet()

					constraintSet.clone(navLayout)
					constraintSet.connect(
						R.id.navigationDemoText, ConstraintSet.TOP, R.id.navLayout,
						ConstraintSet.TOP, 10
					)
					constraintSet.connect(
						R.id.navigationDemoText, ConstraintSet.END, R.id.navLayout,
						ConstraintSet.END, 10
					)
					constraintSet.clear(R.id.navigationDemoText, ConstraintSet.START)
					constraintSet.applyTo(navLayout)

					constraintSet.clone(navLayout)
					constraintSet.connect(
						R.id.navigationSpeedPanel, ConstraintSet.TOP, R.id.navigationDemoText,
						ConstraintSet.BOTTOM, 10
					)
					constraintSet.connect(
						R.id.navigationSpeedPanel, ConstraintSet.END, R.id.navLayout,
						ConstraintSet.END, 0
					)
					constraintSet.applyTo(navLayout)

					constraintSet.clone(navLayout)
					constraintSet.connect(
						R.id.navigationLanePanel, ConstraintSet.START, R.id.navigationBottomPanel,
						ConstraintSet.END, 10
					)
					constraintSet.connect(
						R.id.navigationLanePanel, ConstraintSet.BOTTOM, R.id.navLayout,
						ConstraintSet.BOTTOM, 10
					)
					constraintSet.connect(
						R.id.navigationLanePanel, ConstraintSet.END, R.id.navLayout,
						ConstraintSet.END, 10
					)
					constraintSet.applyTo(navLayout)
				}

				Configuration.ORIENTATION_PORTRAIT -> {
					val constraintSet = ConstraintSet()

					constraintSet.clone(navLayout)
					constraintSet.connect(
						R.id.navigationDemoText, ConstraintSet.TOP, R.id.topPanelController,
						ConstraintSet.BOTTOM, 10
					)
					constraintSet.connect(
						R.id.navigationDemoText, ConstraintSet.END, R.id.navLayout,
						ConstraintSet.END, 10
					)
					constraintSet.connect(
						R.id.navigationDemoText, ConstraintSet.START, R.id.navLayout,
						ConstraintSet.START, 10
					)
					constraintSet.applyTo(navLayout)

					constraintSet.clone(navLayout)
					constraintSet.connect(
						R.id.navigationSpeedPanel, ConstraintSet.TOP, R.id.navigationDemoText,
						ConstraintSet.BOTTOM, 10
					)
					constraintSet.connect(
						R.id.navigationSpeedPanel, ConstraintSet.END, R.id.navLayout,
						ConstraintSet.END, 0
					)
					constraintSet.applyTo(navLayout)

					constraintSet.clone(navLayout)
					constraintSet.connect(
						R.id.navigationLanePanel, ConstraintSet.START, R.id.navLayout,
						ConstraintSet.START, 10
					)
					constraintSet.connect(
						R.id.navigationLanePanel, ConstraintSet.BOTTOM, R.id.navigationBottomPanel,
						ConstraintSet.TOP, 10
					)
					constraintSet.connect(
						R.id.navigationLanePanel, ConstraintSet.END, R.id.navLayout,
						ConstraintSet.END, 10
					)
					constraintSet.applyTo(navLayout)
				}

				else -> return
			}
		}
	}

	// ---------------------------------------------------------------------------------------------

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

		Handler(Looper.getMainLooper()).postDelayed({
			if (demoAnimationRunning)
				updateDemoTextAnimation(duration, steps)
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
		updateSpeedPanel(navDataProvider.info)
		updateNavigationTopPanel(navDataProvider.info)

		updateDemoPanel(navDataProvider.info)
	}

	private fun updateNavigationBottomPanel(navInfo: UINavDataProvider.NavInfo) {
		navigationBottomPanel.visibility = View.VISIBLE

		val color = Util.getColor(navInfo.rttColor.value())

//			val trgba = navInfo.rttColor
//			Color.argb(trgba.alpha(), trgba.red(), trgba.green(), trgba.blue())

		navMenuETAText.text = navInfo.eta
		navMenuETAUnit.text = navInfo.etaUnit
		navMenuTripDurationText.text = navInfo.rtt
		navMenuTripDurationText.setTextColor(color)
		navMenuTripDurationUnit.text = navInfo.rttUnit
		navMenuTripDurationUnit.setTextColor(color)
		navMenuDistanceText.text = navInfo.rtd
		navMenuDistanceUnit.text = navInfo.rtdUnit
	}

	private fun updateNavigationTopPanel(navInfo: UINavDataProvider.NavInfo) {
		topPanelController?.visibility = View.VISIBLE

		val laneInfoImage = this.laneInfoImage

		if (laneInfoImage != null) {
			navigationLanePanel.visibility = View.VISIBLE
			laneInformationImage.visibility = View.VISIBLE
			laneInformationImage.setImageBitmap(laneInfoImage)

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
		if (navInfo.currentSpeed.isNullOrEmpty()) {
			navigationSpeedPanel.visibility = View.GONE
			return
		}

		navigationSpeedPanel.visibility = View.VISIBLE

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
			GEMSdkCall.checkCurrentThread()

			val navInstr = from ?: return Pair(0, null)

			var resultWidth = width
			if (resultWidth == 0) {
				resultWidth = (2.5 * height).toInt()
			}

			val bkColor = TRgba(0, 0, 0, 255)
			val activeColor = TRgba(255, 255, 255, 255)
			val inactiveColor = TRgba(100, 100, 100, 255)

			val image = navInstr.getLaneImage()

			val resultPair = Util.createBitmap(
				image, resultWidth, height, bkColor, activeColor, inactiveColor
			)

			resultWidth = resultPair.first

			return Pair(resultWidth, resultPair.second)
		}
	}

	fun updatePositionInfo(
		value: Position,
		navInstr: NavigationInstruction?,
		willReset: Boolean = true
	) {
		GEMSdkCall.checkCurrentThread()
		if (willReset) resetPositionInfo()

		if (!value.hasSpeed()) return

		var speedLimit = 0.0
		if (navInstr != null && navInstr.getNavigationStatus() == GEMError.KNoError.value) {
			speedLimit = navInstr.getCurrentStreetSpeedLimit()
		}

		val speed = value.getSpeed()
		info.isOverspeeding = (speedLimit > 0.0) && (speed > speedLimit)

		val speedTextPair = UtilUITexts.getSpeedText(
			speed, CommonSettings().getUnitSystem()
		)

		info.currentSpeed = speedTextPair.first
		info.currentSpeedUnit = speedTextPair.second

		if (speedLimit > 0.0) {
			info.currentSpeedLimit = UtilUITexts.getSpeedText(
				speedLimit, CommonSettings().getUnitSystem()
			).first
		}
	}

	fun updateNavigationInfo(
		navInstr: NavigationInstruction?,
		route: Route?,
		willReset: Boolean = true
	) {
		GEMSdkCall.checkCurrentThread()

		if (willReset) resetNavigationInfo()

		if (navInstr == null || route == null) return

		val getRemainingTravelTime: () -> Int = {
			val totalTime = if (navInstr.getNavigationStatus() == GEMError.KNoError.value) {
				navInstr.getRemainingTravelTimeDistance()?.getTotalTime() ?: 0
			} else {
				route.getTimeDistance()?.getTotalTime() ?: 0
			}

			totalTime + Util.getTrafficEventsDelay(route, true)
		}

		val getRemainingTravelDistance: () -> Int = {
			if (navInstr.getNavigationStatus() == GEMError.KNoError.value)
				navInstr.getRemainingTravelTimeDistance()?.getTotalDistance() ?: 0
			else
				route.getTimeDistance()?.getTotalDistance() ?: 0
		}

		val arrivalTime = Time()

		arrivalTime.localTime()
		arrivalTime.fromInt(arrivalTime.asInt() + getRemainingTravelTime() * 1000)

		info.eta = String.format("%d:%02d", arrivalTime.getHour(), arrivalTime.getMinute())
		info.etaUnit = null

		val pairRemainingTravelText = getDistText(
			getRemainingTravelDistance(), CommonSettings().getUnitSystem(), true
		)

		info.rtd = pairRemainingTravelText.first
		info.rtdUnit = pairRemainingTravelText.second

		val rttPair = UtilUITexts.getTimeTextWithDays(getRemainingTravelTime())
		info.rtt = rttPair.first
		info.rttUnit = rttPair.second

		val trafficDelayInMinutes = Util.getTrafficEventsDelay(route, true) / 60

		info.rttColor = when {
			trafficDelayInMinutes == 0 -> {
				// green
				TRgba(0, 170, 0, 255)
			}
			trafficDelayInMinutes < Util.BIG_TRAFFIC_DELAY_IN_MINUTES -> {
				// orange
				TRgba(255, 100, 0, 255)
			}
			else -> {
				// red
				TRgba(235, 0, 0, 255)
			}
		}
	}

	/**---------------------------------------------------------------------------------------------
	Private methods
	----------------------------------------------------------------------------------------------*/


	// ---------------------------------------------------------------------------------------------

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
		info.rttColor = TRgba(0, 0, 0, 0)
	}

	/**---------------------------------------------------------------------------------------------
	data class NavInfo
	----------------------------------------------------------------------------------------------*/
	data class NavInfo(
		var demoText: String? = null,
		var eta: String? = null,
		var etaUnit: String? = null,
		var rtt: String? = null,
		var rttUnit: String? = null,
		var rttColor: TRgba = TRgba(0, 0, 0, 0),
		var rtd: String? = null,
		var rtdUnit: String? = null,
		var currentSpeed: String? = null,
		var currentSpeedUnit: String? = null,
		var currentSpeedLimit: String? = null,
		var isOverspeeding: Boolean = false
	)
}
