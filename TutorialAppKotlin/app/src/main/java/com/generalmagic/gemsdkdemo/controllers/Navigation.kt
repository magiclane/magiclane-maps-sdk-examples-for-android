// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.controllers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.models.*
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.activities.RouteDescriptionActivity
import com.generalmagic.gemsdkdemo.util.MainMapStatusFollowingProvider
import com.generalmagic.gemsdkdemo.util.StaticsHolder
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import kotlinx.android.synthetic.main.nav_layout.view.*
import kotlinx.android.synthetic.main.pick_location.view.*


// ---------------------------------------------------------------------------------------------
// BASE
// ---------------------------------------------------------------------------------------------

abstract class BaseNavControllerLayout(context: Context?, attrs: AttributeSet?) :
	AppLayoutController(context, attrs) {

	protected val routeCalcListener = object : ProgressListener() {
		override fun notifyStart(hasProgress: Boolean) {
			Handler(Looper.getMainLooper()).post {
				showProgress()
				navLayout?.let {
					bottomButtons()?.bottomLeftButton?.let {
						it.visibility = View.VISIBLE
						it.setOnClickListener {
							GEMSdkCall.execute {
								doStop()
							}
						}
						buttonAsStop(it)
					}
				}
			}

			GEMSdkCall.execute {
				StaticsHolder.getMainMapView()?.getPreferences()?.routes()?.clear()
			}
		}

		override fun notifyComplete(reason: Int, hint: String) {
			Handler(Looper.getMainLooper()).post {
				hideProgress()

				val gemError = GEMError.fromInt(reason)
				if (gemError != GEMError.KNoError) {
					Toast.makeText(context, "Routing service error: $gemError", Toast.LENGTH_SHORT)
						.show()
					return@post
				}

				navLayout?.routeUpdated(GEMSdkCall.execute { getRoute() })
			}
		}
	}

	protected val navigationListener = object : NavigationListener() {
		private var startNotified = false
		override fun betterRouteDetected(
			route: Route,
			travelTime: Int,
			delay: Int,
			timeGain: Int
		) {
		}

		override fun betterRouteInvalidated() {}

		override fun betterRouteRejected(errorCode: Int) {}

		override fun canPlayNavigationSound(): Boolean {
			return false
		}

		override fun destinationReached(landmark: Landmark) {
			Handler(Looper.getMainLooper()).post {
				onNavigationEnded()
				startNotified = false
			}
		}

		override fun navigationError(errorCode: Int) {
			Handler(Looper.getMainLooper()).post {
				onNavigationEnded(errorCode)
				startNotified = false
			}
		}

		override fun navigationInstructionUpdated() {
			Handler(Looper.getMainLooper()).post {
				if (!startNotified) {
					onNavigationStarted()
					startNotified = true
				}

				navLayout?.updateNavInstruction(GEMSdkCall.execute { getNavigationInstruction() })
			}
		}

		override fun navigationSound(sound: ISound) {}

		override fun routeUpdated() {
			Handler(Looper.getMainLooper()).post {
				navLayout?.routeUpdated(GEMSdkCall.execute { getRoute() })
			}
		}

		override fun waypointReached(landmark: Landmark) {}
	}

	protected abstract fun isNavigationActive(): Boolean
	protected abstract fun getRoute(): Route?
	protected abstract fun getNavigationInstruction(): NavigationInstruction?

	protected open fun onNavigationStarted() {}

	protected open fun onNavigationEnded(errorCode: Int = GEMError.KNoError.value) {}

}

// ---------------------------------------------------------------------------------------------

abstract class BaseTurnByTurnLayout(context: Context?, attrs: AttributeSet?) :
	BaseNavControllerLayout(context, attrs) {

	private val positionListener = object : PositionListener() {
		override fun positionUpdated(value: Position) {
			Handler(Looper.getMainLooper()).post {
				navLayout.updatePosition(value)
			}
		}
	}

	private lateinit var alarmService: AlarmService
	private val nAlarmDistanceMeters = 500.0
	private var alarmListener: AlarmListener = object : AlarmListener() {
		override fun boundaryCrossed() {}

		override fun highSpeed(speed: Double) {}

		override fun landmarkAlarmsPassedOver() {}

		override fun landmarkAlarmsUpdated() {}

		override fun lowSpeed(speed: Double) {}

		override fun markerAlarmsPassedOver() {}

		override fun markerAlarmsUpdated() {
			Handler(Looper.getMainLooper()).post { navLayout.updateAlarmsInfo(alarmService) }
		}

		override fun monitoringStateChanged(isMonitoringActive: Boolean) {}

		override fun normalSpeed() {}

		override fun speedLimit(speed: Double, limit: Double) {

		}

		override fun tunnelEntered() {}

		override fun tunnelLeft() {}
	}

	init {
		alarmService = AlarmService(alarmListener)
		alarmService.setLandmarkAlarmDistance(nAlarmDistanceMeters)

		val availableOverlays = MarkerOverlaysService().getAvailableOverlays(null)?.first
		if (availableOverlays != null) {
			for (item in availableOverlays) {
				if (item.getUid() == TCommonOverlayId.EOID_Safety.value.toShort()) {
					alarmService.markers()?.add(item)
				}
			}
		}
	}

	override fun onMapFollowStatusChanged(following: Boolean) {
		if (following) {
			visibility = View.VISIBLE
			hideAppBar()
			hideSystemBars()
			disableScreenLock()

			val active = GEMSdkCall.execute { isNavigationActive() } ?: false
			if (active) doNavBotButtons()
			else doNotNavBotButtons()
		} else {
			visibility = View.GONE
			showAppBar()
			showSystemBars()
			enableScreenLock()

			doNotFollowingBotButtons()
		}
	}

	override fun onNavigationStarted() {
		doNavBotButtons()

		val mainMap = StaticsHolder.getMainMapView() ?: return

		GEMSdkCall.execute {
			mainMap.getPreferences()?.enableCursor(false)

			val route = getRoute()
			if (route != null) mainMap.getPreferences()?.routes()?.add(route, true)

			PositionService().registerPositionListener(positionListener, 1000)
			MainMapStatusFollowingProvider.getInstance().doFollow()
		}

		showNavLayout()
	}

	override fun onNavigationEnded(errorCode: Int) {
		hideNavLayout()

		showAppBar()
		showSystemBars()
		enableScreenLock()

		doNotNavBotButtons()

		val mainMap = StaticsHolder.getMainMapView() ?: return

		GEMSdkCall.execute {
			PositionService().unregisterPositionListener(positionListener)
			mainMap.getPreferences()?.routes()?.clear()
//			MainMapStatusFollowingProvider.getInstance().doUnFollow()
		}
	}

	private fun showNavLayout() {
		navLayout.visibility = View.VISIBLE
		navLayout.showNavInfo()
		doNavBotButtons()
	}

	private fun hideNavLayout() {
		navLayout.visibility = View.GONE
		navLayout.hideNavInfo()
	}

	private fun doNavBotButtons() {
		navBottomButtons?.let { buttons ->
			buttons.navBottomLeftButton?.let {
				it.visibility = View.VISIBLE
				buttonAsStop(it)
				it.setOnClickListener {
					GEMSdkCall.execute { doStop() }
				}
			}
			buttons.navBottomRightButton?.let {
				it.visibility = View.VISIBLE
				buttonAsDescription(it)
				it.setOnClickListener {
					doShowRouteDescription()
				}
			}
		}

		bottomButtons()?.let { buttons ->
			buttons.bottomLeftButton?.visibility = View.GONE
			buttons.bottomCenterButton?.visibility = View.GONE
			buttons.bottomRightButton?.visibility = View.GONE
		}
	}

	private fun doNotNavBotButtons() {
		bottomButtons()?.let { bottomButtons ->
			bottomButtons.bottomCenterButton?.visibility = View.GONE
			bottomButtons.bottomRightButton?.visibility = View.GONE
			bottomButtons.bottomLeftButton?.let {
				it.visibility = View.VISIBLE
				buttonAsStart(it)
				it.setOnClickListener {
					GEMSdkCall.execute { doStart() }
				}
			}
		}
	}

	private fun doNotFollowingBotButtons() {
		bottomButtons()?.let { buttons ->
			buttons.bottomCenterButton?.visibility = View.GONE
			buttons.bottomRightButton?.visibility = View.GONE
			buttons.bottomLeftButton?.let {
				it.visibility = View.VISIBLE
				buttonAsFollowGps(it)
				it.setOnClickListener {
					GEMSdkCall.execute {
						MainMapStatusFollowingProvider.getInstance().doFollow()
					}
				}
			}
		}
	}

	private fun doShowRouteDescription() {
		val route = GEMSdkCall.execute { getRoute() }
		if (route == null) {
			Toast.makeText(context, "No route to display!", Toast.LENGTH_SHORT).show()
			return
		}

		RouteDescriptionActivity.showRouteDescription(context, route)
	}
}

open class BaseSimulationController(context: Context?, attrs: AttributeSet?) :
	BaseTurnByTurnLayout(context, attrs) {
	protected val simulationService = SimulationService()

	fun doStart(waypoints: ArrayList<Landmark>) {
		GEMSdkCall.checkCurrentThread()
		if (isNavigationActive()) return

		doStop() // stop any sim in progress

		val preferences = RoutePreferences()
		preferences.setTransportMode(TTransportMode.ETM_Car)
		preferences.setRouteType(TRouteType.ERT_Fastest)
		preferences.setAvoidTraffic(true)

		simulationService.startSimulation(
			waypoints, preferences, navigationListener, routeCalcListener
		)
	}

	override fun getRoute(): Route? {
		GEMSdkCall.checkCurrentThread()
		return simulationService.getSimulationRoute()
	}

	override fun getNavigationInstruction(): NavigationInstruction? {
		GEMSdkCall.checkCurrentThread()
		return simulationService.getSimulationInstruction()
	}

	override fun doStop() {
		GEMSdkCall.checkCurrentThread()
		simulationService.cancelSimulation()
	}

	override fun isNavigationActive(): Boolean {
		GEMSdkCall.checkCurrentThread()
		return simulationService.isSimulationActive()
	}

	override fun onNavigationStarted() {
		super.onNavigationStarted()
		navLayout.setIsDemo(true)
	}
}

open class BaseNavigationController(context: Context?, attrs: AttributeSet?) :
	BaseTurnByTurnLayout(context, attrs) {
	protected val navigationService = NavigationService()

	fun doStart(waypoints: ArrayList<Landmark>) {
		GEMSdkCall.checkCurrentThread()
		if (isNavigationActive()) return

		doStop() // stop any sim in progress

		val preferences = RoutePreferences()
		preferences.setTransportMode(TTransportMode.ETM_Car)
		preferences.setRouteType(TRouteType.ERT_Fastest)
		preferences.setAvoidTraffic(true)

		navigationService.startNavigation(
			waypoints, preferences, navigationListener, routeCalcListener
		)
	}

	override fun getRoute(): Route? {
		GEMSdkCall.checkCurrentThread()
		return navigationService.getNavigationRoute()
	}

	override fun getNavigationInstruction(): NavigationInstruction? {
		GEMSdkCall.checkCurrentThread()
		return navigationService.getNavigationInstruction()
	}

	override fun doStop() {
		GEMSdkCall.checkCurrentThread()
		if (navigationService.isNavigationActive())
			navigationService.cancelNavigation()
	}

	override fun isNavigationActive(): Boolean {
		GEMSdkCall.checkCurrentThread()
		return navigationService.isNavigationActive()
	}

	override fun onNavigationStarted() {
		super.onNavigationStarted()
		navLayout.setIsDemo(false)
	}
}

// ---------------------------------------------------------------------------------------------
// PREDEFINED
// ---------------------------------------------------------------------------------------------

open class PredefSimController(context: Context?, attrs: AttributeSet?) :
	BaseSimulationController(context, attrs) {

	private fun getWaypoints(): ArrayList<Landmark> {
		return arrayListOf(
			Landmark("San Francisco", QualifiedCoordinates(37.77903, -122.41991)),
			Landmark("San Jose", QualifiedCoordinates(37.33619, -121.89058))
		)

//		return arrayListOf(
//			Landmark("London", QualifiedCoordinates(51.5073204, -0.1276475)),
//			Landmark("Paris", QualifiedCoordinates(48.8566932, 2.3514616))
//		)

//		return arrayListOf(
//			Landmark("Brasov", QualifiedCoordinates(45.638936, 25.623869)),
//			Landmark("Brasov", QualifiedCoordinates(45.630153, 25.639361))
//		)
	}

	override fun doStart() {
		doStart(getWaypoints())
	}
}

open class PredefNavController(context: Context?, attrs: AttributeSet?) :
	BaseNavigationController(context, attrs) {

	private val positionService = PositionService()

	private val preferences = SearchPreferences()
	private val search = object : SearchServiceWrapper() {
		override fun onSearchStarted() {
			showProgress()
		}

		override fun onSearchCompleted(reason: Int) {
			val gemError = GEMError.fromInt(reason)
			if (gemError == GEMError.KCancel) return

			hideProgress()

			if (gemError != GEMError.KNoError) {
				Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
				return
			}

			val list = results.asArrayList()
			if (list.isEmpty()) {
				Toast.makeText(context, "No search results!", Toast.LENGTH_SHORT).show()
				return
			}

			GEMSdkCall.execute { doStart(arrayListOf(list[0])) }
		}
	}

	override fun doStart() {
		doSearch()
	}

	private fun getGasCategory(): LandmarkCategory? {
		GEMSdkCall.checkCurrentThread()
		val categoriesList = GenericCategories().getCategories() ?: return null

		var category: LandmarkCategory? = null
		if (categoriesList.size > 0) {
			category = categoriesList[0]
		}
		return category
	}

	private fun doSearch() {
		GEMSdkCall.checkCurrentThread()

		val liveDataSource = GMDataSourceFactory.createLive()

		val posServiceError = GEMError.fromInt(positionService.startWithDataSource(liveDataSource))
		if (posServiceError != GEMError.KNoError) {
			Handler(Looper.getMainLooper()).post {
				Toast.makeText(context, "PositionService: $posServiceError", Toast.LENGTH_SHORT)
					.show()
			}
			return
		}

		val myPos = positionService.getPosition() ?: return
		val myPosition = myPos.getQualifiedCoordinates()?.coordinates() ?: return

		val category = getGasCategory()

		preferences.setReferencePoint(myPosition)
		if (category != null)
			preferences.lmks()?.addStoreCategoryId(category.getLandmarkStoreId(), category.getId())

		search.service.searchAroundPosition(
			search.results, search.listener, myPosition, "", preferences
		)
	}
}

// ---------------------------------------------------------------------------------------------
// CUSTOM
// ---------------------------------------------------------------------------------------------

class CustomSimController(context: Context?, attrs: AttributeSet?) :
	BaseSimulationController(context, attrs) {
	private val landmarks = ArrayList<Landmark>()

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		pickLocation?.let {
			it.onCancelPressed = {
				bottomButtons()?.bottomLeftButton?.visibility = View.GONE
				bottomButtons()?.bottomCenterButton?.visibility = View.GONE
				bottomButtons()?.bottomRightButton?.visibility = View.GONE
				pickLocation.visibility = View.GONE
				GEMSdkCall.execute { MainMapStatusFollowingProvider.getInstance().doUnFollow() }
			}
			it.onStartPicked = { landmark ->
				landmarks.add(0, landmark)
			}
			it.onIntermediatePicked = { landmark ->
				landmarks.add(landmark)
				landmark.setName("Intermediate")
			}

			it.onDestinationPicked = { landmark ->
				pickLocation.visibility = View.GONE
				landmarks.add(landmark)
				GEMSdkCall.execute { doStart(landmarks) }
			}
		}
	}

	override fun doStart() {
		GEMSdkCall.checkCurrentThread()

		landmarks.clear()

		Handler(Looper.getMainLooper()).post {
			pickLocation?.pickStart()
		}
	}

	override fun onMapFollowStatusChanged(following: Boolean) {
		if (pickLocation.visibility != View.GONE) return
		super.onMapFollowStatusChanged(following)
	}
}

class CustomNavController(context: Context?, attrs: AttributeSet?) :
	BaseNavigationController(context, attrs) {
	private val landmarks = ArrayList<Landmark>()

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		pickLocation?.let {
			it.onCancelPressed = {
				bottomButtons()?.bottomLeftButton?.visibility = View.GONE
				bottomButtons()?.bottomCenterButton?.visibility = View.GONE
				bottomButtons()?.bottomRightButton?.visibility = View.GONE
				pickLocation.visibility = View.GONE
				GEMSdkCall.execute { MainMapStatusFollowingProvider.getInstance().doUnFollow() }
			}
			it.onStartPicked = { landmark ->
				landmarks.add(0, landmark)
			}
			it.onIntermediatePicked = { landmark ->
				landmarks.add(landmark)
				landmark.setName("Intermediate")
			}

			it.onDestinationPicked = { landmark ->
				pickLocation.visibility = View.GONE
				landmarks.add(landmark)

				GEMSdkCall.execute { doStart(landmarks) }
			}
		}
	}

	override fun doStart() {
		GEMSdkCall.checkCurrentThread()

		landmarks.clear()

		Handler(Looper.getMainLooper()).post {
			pickLocation?.pickDestination()
		}
	}

	override fun onMapFollowStatusChanged(following: Boolean) {
		if (pickLocation.visibility != View.GONE) return
		super.onMapFollowStatusChanged(following)
	}
}

// ---------------------------------------------------------------------------------------------

