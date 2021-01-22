/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.activities.RouteDescriptionActivity
import com.generalmagic.gemsdk.demo.activities.settings.SettingsProvider
import com.generalmagic.gemsdk.demo.activities.settings.TIntSettings
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.MapLayoutController
import com.generalmagic.gemsdk.demo.app.Tutorials
import com.generalmagic.gemsdk.demo.app.TutorialsOpener
import com.generalmagic.gemsdk.demo.app.elements.ButtonsDecorator
import com.generalmagic.gemsdk.demo.util.IntentHelper
import com.generalmagic.gemsdk.models.*
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.nav_layout.view.*
import kotlinx.android.synthetic.main.pick_location.view.*

// BASE

abstract class BaseNavControllerLayout(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

    protected val navigationService = NavigationService()

    protected val routeCalcListener = object : ProgressListener() {
        override fun notifyStart(hasProgress: Boolean) {
            GEMApplication.postOnMain {
                showProgress()
                navLayout?.let { setStopButtonVisible(true) }
            }

            GEMApplication.clearMapVisibleRoutes()
        }

        override fun notifyComplete(reason: Int, hint: String) {
            val gemError = GEMError.fromInt(reason)
            val route = getRoute()

            GEMApplication.postOnMain {
                hideProgress()
            }
            
            if (gemError == GEMError.KNoError) {
                navLayout?.routeUpdated(route)
            } else {
                GEMApplication.postOnMain {
                    Toast.makeText(
                        context,
                        "Routing service error: $gemError",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    protected val navigationListener = object : NavigationListener() {
        private var startNotified = false
        override fun onBetterRouteDetected(
            route: Route,
            travelTime: Int,
            delay: Int,
            timeGain: Int
        ) {
        }

        override fun onBetterRouteInvalidated() {}

        override fun onBetterRouteRejected(errorCode: Int) {}

        override fun canPlayNavigationSound(): Boolean {
            return false
        }

        override fun onDestinationReached(landmark: Landmark) {
            GEMApplication.postOnMain {
                onNavigationEnded()
                startNotified = false
            }
        }

        override fun onNavigationError(errorCode: Int) {
            GEMApplication.postOnMain {
                onNavigationEnded(errorCode)
                startNotified = false
            }
        }

        override fun onNavigationInstructionUpdated(instr: NavigationInstruction) {
            GEMApplication.postOnMain {
                if (!startNotified) {
                    onNavigationStarted()
                    startNotified = true
                }

                navLayout?.updateNavInstruction(instr)
            }
        }

        override fun onNavigationSound(sound: ISound) {}

        override fun onRouteUpdated(route: Route) {
            navLayout?.routeUpdated(route)
        }

        override fun onWaypointReached(landmark: Landmark) {}

        override fun onNavigationStarted() {
            GEMApplication.postOnMain {
                this@BaseNavControllerLayout.onNavigationStarted()
            }
        }
    }

    fun isTripActive(): Boolean {
        return GEMSdkCall.execute { navigationService.isTripActive(navigationListener) } ?: false
    }

    fun isDemo(): Boolean {
        return GEMSdkCall.execute { navigationService.isSimulationActive(navigationListener) }
            ?: false
    }

    fun getRoute(): Route? {
        return GEMSdkCall.execute { navigationService.getNavigationRoute(navigationListener) }
    }

    fun getNavigationInstruction(): NavigationInstruction? {
        return GEMSdkCall.execute { navigationService.getNavigationInstruction(navigationListener) }
    }

    override fun doStop() {
        GEMSdkCall.execute { navigationService.cancelNavigation(navigationListener) }
    }

    protected open fun onNavigationStarted() {}

    protected open fun onNavigationEnded(errorCode: Int = GEMError.KNoError.value) {}

    private fun doShowRouteDescription() {
        val route = getRoute()
        if (route == null) {
            Toast.makeText(context, "No route to display!", Toast.LENGTH_SHORT).show()
            return
        }

        RouteDescriptionActivity.showRouteDescription(context, route)
    }

    fun setNavInfoButtonVisible(visible: Boolean) {
        val button = navBottomRightButton ?: return

        if (visible) {
            ButtonsDecorator.buttonAsInfo(context, button) {
                doShowRouteDescription()
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    fun setNavStopButtonVisible(visible: Boolean) {
        val button = navBottomLeftButton ?: return

        if (visible) {
            ButtonsDecorator.buttonAsStop(context, navBottomLeftButton) {
                GEMSdkCall.execute { doStop() }
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }
}

abstract class BaseTurnByTurnLayout(context: Context, attrs: AttributeSet?) :
    BaseNavControllerLayout(context, attrs) {

    private val positionListener = object : PositionListener() {
        override fun onNewPosition(value: PositionData) {
            navLayout.updatePosition(value)
        }
    }

    private var alarmService: AlarmService? = null
    private val nAlarmDistanceMeters = 500.0
    private var alarmListener: AlarmListener = object : AlarmListener() {
        override fun onBoundaryCrossed() {}

        override fun onHighSpeed(speed: Double) {}

        override fun onLandmarkAlarmsPassedOver() {}

        override fun onLandmarkAlarmsUpdated() {}

        override fun onLowSpeed(speed: Double) {}

        override fun onMarkerAlarmsPassedOver() {}

        override fun onMarkerAlarmsUpdated() {
            GEMApplication.postOnMain { navLayout.updateAlarmsInfo(alarmService) }
        }

        override fun onMonitoringStateChanged(isMonitoringActive: Boolean) {}

        override fun onNormalSpeed() {}

        override fun onSpeedLimit(speed: Double, limit: Double) {
        }

        override fun onTunnelEntered() {}

        override fun onTunnelLeft() {}
    }

    init {
        GEMSdkCall.execute {
            alarmService = AlarmService.produce(alarmListener)
            alarmService?.setLandmarkAlarmDistance(nAlarmDistanceMeters)

            val availableOverlays = MarkerOverlaysService().getAvailableOverlays(null)?.first
            if (availableOverlays != null) {
                for (item in availableOverlays) {
                    if (item.getUid() == TCommonOverlayId.EOID_Safety.value.toShort()) {
                        alarmService?.markers()?.add(item)
                    }
                }
            }
        }
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        if (following) {
            visibility = View.VISIBLE
            navLayout.showNavInfo()
            GEMApplication.setAppBarVisible(false)
            GEMApplication.setSystemBarsVisible(false)
            setScreenAlwaysOn(true)

            if (isTripActive()) doNavBotButtons()
            else doNotNavBotButtons()
        } else {
            visibility = View.GONE
            navLayout.hideNavInfo()
            GEMApplication.setAppBarVisible(true)
            GEMApplication.setSystemBarsVisible(true)
            setScreenAlwaysOn(false)

            doNotFollowingBotButtons()
        }
    }

    override fun onNavigationStarted() {
        navLayout.setIsDemo(isDemo())
        doNavBotButtons()

        val mainMap = GEMApplication.getMainMapView() ?: return

        GEMSdkCall.execute {
            mainMap.preferences()?.enableCursor(false)

            val route = getRoute()
            if (route != null) mainMap.preferences()?.routes()?.add(route, true)

            PositionService().addListener(positionListener, EDataType.Position)
            GEMApplication.doMapFollow()
        }
        showNavLayout()
    }

    override fun onNavigationEnded(errorCode: Int) {
        hideNavLayout()

        GEMApplication.setAppBarVisible(true)
        GEMApplication.setSystemBarsVisible(true)
        setScreenAlwaysOn(false)

        val mainMap = GEMApplication.getMainMapView() ?: return

        GEMSdkCall.execute {
            PositionService().removeListener(positionListener)
            mainMap.preferences()?.routes()?.clear()
// 			MainMapStatusFollowingProvider.getInstance().doUnFollow()
        }
        Tutorials.openHelloWorldTutorial()
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
        hideAllButtons()

        setNavStopButtonVisible(true)
        setNavInfoButtonVisible(true)
    }

    private fun doNotNavBotButtons() {
        hideAllButtons()
        setStartButtonVisible(true)
    }

    private fun doNotFollowingBotButtons() {
        hideAllButtons()
        setFollowGpsButtonVisible(true)
    }

    private fun doShowRouteDescription() {
        val route = getRoute()
        if (route == null) {
            Toast.makeText(context, "No route to display!", Toast.LENGTH_SHORT).show()
            return
        }

        RouteDescriptionActivity.showRouteDescription(context, route)
    }
}

open class BaseSimulationController(context: Context, attrs: AttributeSet?) :
    BaseTurnByTurnLayout(context, attrs) {

    private val progressListener = object : ProgressListener() {}

    fun doStart(waypoints: ArrayList<Landmark>) {
        if (isTripActive()) return
        if (waypoints.size < 2) return

        doStop() // stop any sim in progress

        GEMSdkCall.execute {
            var speedMultiplier = 1
            SettingsProvider.getIntValue(TIntSettings.EDemoSpeed.value).let {
                if (it.second > 0)
                    speedMultiplier = it.second
            }

            val preferences = SettingsProvider.loadRoutePreferences()
            preferences.setTransportMode(TTransportMode.ETM_Car)
            preferences.setAvoidTraffic(true)

            navigationService.startSimulation(
                waypoints,
                preferences,
                navigationListener,
                routeCalcListener,
                speedMultiplier.toFloat()
            )
        }
    }

    fun doStart(route: Route?) {
        route ?: return
        if (isTripActive()) return

        doStop() // stop any sim in progress

        navLayout?.routeUpdated(route)

        GEMSdkCall.execute {
            var speedMultiplier = 1
            SettingsProvider.getIntValue(TIntSettings.EDemoSpeed.value).let {
                if (it.second > 0)
                    speedMultiplier = it.second
            }

            val preferences = SettingsProvider.loadRoutePreferences()
            preferences.setTransportMode(TTransportMode.ETM_Car)
            preferences.setAvoidTraffic(true)

            navigationService.startSimulationWithRoute(
                route,
                navigationListener,
                progressListener,
                speedMultiplier.toFloat()
            )
        }
    }
}

open class BaseNavigationController(context: Context, attrs: AttributeSet?) :
    BaseTurnByTurnLayout(context, attrs) {
    fun doStart(waypoints: ArrayList<Landmark>) {
        if (isTripActive()) return

        doStop() // stop any sim in progress

        GEMSdkCall.execute {
            val preferences = SettingsProvider.loadRoutePreferences()
            preferences.setTransportMode(TTransportMode.ETM_Car)
            preferences.setAvoidTraffic(true)

            navigationService.startNavigation(
                waypoints, preferences, navigationListener, routeCalcListener
            )
        }
    }
}

// PREDEFINED

open class SimLandmarksController(context: Context, attrs: AttributeSet?) :
    BaseSimulationController(context, attrs) {
    override fun doStart() {
        val waypoints = IntentHelper.getObjectForKey(EXTRA_WAYPOINTS) as ArrayList<Landmark>?
        waypoints ?: return

        doStart(waypoints)
    }

    companion object {
        const val EXTRA_WAYPOINTS = "waypoints"
    }
}

open class SimRouteController(context: Context, attrs: AttributeSet?) :
    BaseSimulationController(context, attrs) {
    override fun doStart() {
        val route = IntentHelper.getObjectForKey(EXTRA_ROUTE) as Route?
        route ?: return

        doStart(route)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

open class PredefNavController(context: Context, attrs: AttributeSet?) :
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

            doStart(arrayListOf(list[0]))
        }
    }

    override fun doStart() {
        GEMSdkCall.execute { doSearch() }
    }

    private fun getGasCategory(): LandmarkCategory? = GEMSdkCall.execute {
        val categoriesList = GenericCategories().getCategories() ?: return@execute null

        var category: LandmarkCategory? = null
        if (categoriesList.size > 0) {
            category = categoriesList[0]
        }
        return@execute category
    }

    private fun doSearch() {
        GEMSdkCall.checkCurrentThread()

        val liveDataSource = GMDataSourceFactory.produceLive()
        liveDataSource ?: return

        val posServiceError = GEMError.fromInt(positionService.setDataSource(liveDataSource))
        if (posServiceError != GEMError.KNoError) {
            GEMApplication.postOnMain {
                Toast.makeText(context, "PositionService: $posServiceError", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        val myPos = positionService.getPosition() ?: return
        val myPosition = Coordinates(myPos.getLatitude(), myPos.getLongitude())

        val category = getGasCategory()

        preferences.setReferencePoint(myPosition)
        if (category != null) {
            preferences.lmks()
                ?.addStoreCategoryId(category.getLandmarkStoreId(), category.getId())
        }

        search.service.searchAroundPosition(
            search.results,
            search.listener,
            myPosition,
            "",
            preferences
        )
    }
}

// CUSTOM

class CustomSimController(context: Context, attrs: AttributeSet?) :
    BaseSimulationController(context, attrs) {
    private val landmarks = ArrayList<Landmark>()
    private var picking = false

    override fun onCreated() {
        super.onCreated()

        pickLocation?.let {
            it.onCancelPressed = {
                hideAllButtons()
                pickLocation.visibility = View.GONE

                Tutorials.openHelloWorldTutorial()
            }
            
            it.onStartPicked = { landmark ->
                landmarks.add(0, landmark)
            }
            
            it.onIntermediatePicked = { landmark ->
                landmarks.add(landmark)
                GEMSdkCall.execute { landmark.setName("Intermediate") }
            }

            it.onDestinationPicked = { landmark ->
                pickLocation.visibility = View.GONE
                landmarks.add(landmark)
                GEMSdkCall.execute { doStart(landmarks) }
                picking = false
                TutorialsOpener.onTutorialDestroyed(it)
            }
        }
    }

    override fun doStart() {
        landmarks.clear()

        picking = true
        pickLocation?.mapActivity = mapActivity
        pickLocation?.pickStart()
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        if (pickLocation.visibility != View.GONE) return
        if (picking) return

        super.onMapFollowStatusChanged(following)
    }
}

class CustomNavController(context: Context, attrs: AttributeSet?) :
    BaseNavigationController(context, attrs) {
    private val landmarks = ArrayList<Landmark>()
    private var picking = false

    override fun onCreated() {
        super.onCreated()

        pickLocation?.let {
            it.onCancelPressed = {
                hideAllButtons()
                pickLocation.visibility = View.GONE

                Tutorials.openHelloWorldTutorial()
            }
            it.onStartPicked = { landmark ->
                landmarks.add(0, landmark)
            }
            it.onIntermediatePicked = { landmark ->
                landmarks.add(landmark)
                GEMSdkCall.execute { landmark.setName("Intermediate") }
            }

            it.onDestinationPicked = { landmark ->
                pickLocation.visibility = View.GONE
                landmarks.add(landmark)
                doStart(landmarks)
                picking = false
                TutorialsOpener.onTutorialDestroyed(it)
            }
        }
    }

    override fun doStart() {
        landmarks.clear()

        picking = true
        pickLocation?.mapActivity = mapActivity
        pickLocation?.pickDestination()
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        if (pickLocation.visibility != View.GONE) return
        if (picking) return

        super.onMapFollowStatusChanged(following)
    }
}
