/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.d3scene.ECommonOverlayId
import com.generalmagic.sdk.d3scene.OverlayService
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.activities.RouteDescriptionActivity
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.common.NavPanelsController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.common.PickLocationController
import com.generalmagic.sdk.examples.demo.activities.settings.SettingsProvider
import com.generalmagic.sdk.examples.demo.activities.settings.TIntSettings
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.MapLayoutController
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.examples.demo.app.TutorialsOpener
import com.generalmagic.sdk.examples.demo.app.elements.ButtonsDecorator
import com.generalmagic.sdk.examples.demo.util.IntentHelper
import com.generalmagic.sdk.places.*
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.sensordatasource.*
import com.generalmagic.sdk.sensordatasource.enums.EDataType
import com.generalmagic.sdk.util.SdkCall
import com.google.android.material.floatingactionbutton.FloatingActionButton

// BASE

abstract class BaseNavControllerLayout(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

    lateinit var pickLocation: PickLocationController
    lateinit var navLayout: NavPanelsController
    lateinit var navBottomLeftButton: FloatingActionButton
    lateinit var navBottomRightButton: FloatingActionButton

    protected val navigationService = NavigationService()

    protected val routeCalcListener = object : ProgressListener() {
        override fun notifyStart(hasProgress: Boolean) {
            GEMApplication.postOnMain {
                showProgress()
                setStopButtonVisible(true)
            }

            GEMApplication.clearMapVisibleRoutes()
        }

        override fun notifyComplete(reason: SdkError, hint: String) {
            val route = getRoute()

            GEMApplication.postOnMain {
                hideProgress()
            }

            if (reason == SdkError.NoError) {
                navLayout.routeUpdated(route)
            } else {
                GEMApplication.postOnMain {
                    Toast.makeText(
                        context,
                        "Routing service error: $reason",
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

                navLayout.updateNavInstruction(instr)
            }
        }

        override fun onNavigationSound(sound: ISound) {}

        override fun onRouteUpdated(route: Route) {
            navLayout.routeUpdated(route)
        }

        override fun onWaypointReached(landmark: Landmark) {}

        override fun onNavigationStarted() {
            GEMApplication.postOnMain {
                this@BaseNavControllerLayout.onNavigationStarted()
            }
        }
    }

    override fun onCreated() {
        navLayout = findViewById(R.id.navLayout)
        navBottomLeftButton = navLayout.findViewById(R.id.navBottomLeftButton)
        navBottomRightButton = navLayout.findViewById(R.id.navBottomRightButton)
        super.onCreated()
    }

    fun isTripActive(): Boolean {
        return SdkCall.execute { navigationService.isTripActive(navigationListener) } ?: false
    }

    fun isDemo(): Boolean {
        return SdkCall.execute { navigationService.isSimulationActive(navigationListener) }
            ?: false
    }

    fun getRoute(): Route? {
        return SdkCall.execute { navigationService.getNavigationRoute(navigationListener) }
    }

    fun getNavigationInstruction(): NavigationInstruction? {
        return SdkCall.execute { navigationService.getNavigationInstruction(navigationListener) }
    }

    override fun doStop() {
        SdkCall.execute { navigationService.cancelNavigation(navigationListener) }
    }

    protected open fun onNavigationStarted() {}

    protected open fun onNavigationEnded(errorCode: Int = SdkError.NoError.value) {}

    private fun doShowRouteDescription() {
        val route = getRoute()
        if (route == null) {
            Toast.makeText(context, "No route to display!", Toast.LENGTH_SHORT).show()
            return
        }

        RouteDescriptionActivity.showRouteDescription(context, route)
    }

    fun setNavInfoButtonVisible(visible: Boolean) {
        val button = navBottomRightButton

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
        val button = navBottomLeftButton

        if (visible) {
            ButtonsDecorator.buttonAsStop(context, navBottomLeftButton) {
                SdkCall.execute { doStop() }
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

        override fun onLandmarkAlarmsPassedOver() {}

        override fun onLandmarkAlarmsUpdated() {}

        override fun onOverlayItemAlarmsPassedOver() {}

        override fun onOverlayItemAlarmsUpdated() {
            GEMApplication.postOnMain { navLayout.updateAlarmsInfo(alarmService) }
        }

        override fun onMonitoringStateChanged(isMonitoringActive: Boolean) {}

        override fun onTunnelEntered() {}

        override fun onTunnelLeft() {}
    }

    init {
        SdkCall.execute {
            alarmService = AlarmService.produce(alarmListener)
            alarmService?.alarmDistance = nAlarmDistanceMeters

            val availableOverlays = OverlayService().getAvailableOverlays(null)?.first
            if (availableOverlays != null) {
                for (item in availableOverlays) {
                    if (item.uid == ECommonOverlayId.Safety.value) {
                        alarmService?.overlays?.add(item.uid)
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

        SdkCall.execute {
            mainMap.preferences?.enableCursor = false

            val route = getRoute()
            if (route != null) {
                mainMap.preferences?.routes?.add(route, true)
            }

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

        SdkCall.execute {
            PositionService().removeListener(positionListener)
            mainMap.preferences?.routes?.clear()
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

        SdkCall.execute {
            var speedMultiplier = 1
            SettingsProvider.getIntValue(TIntSettings.EDemoSpeed.value).let {
                if (it.second > 0)
                    speedMultiplier = it.second
            }

            val preferences = SettingsProvider.loadRoutePreferences()
            preferences.setTransportMode(ERouteTransportMode.Car)
            preferences.setAvoidTraffic(true)

            navigationService.startSimulation(
                waypoints,
                navigationListener,
                routeCalcListener,
                preferences,
                speedMultiplier.toFloat()
            )
        }
    }

    fun doStart(route: Route?) {
        route ?: return
        if (isTripActive()) return

        doStop() // stop any sim in progress

        navLayout.routeUpdated(route)

        SdkCall.execute {
            var speedMultiplier = 1
            SettingsProvider.getIntValue(TIntSettings.EDemoSpeed.value).let {
                if (it.second > 0)
                    speedMultiplier = it.second
            }

            val preferences = SettingsProvider.loadRoutePreferences()
            preferences.setTransportMode(ERouteTransportMode.Car)
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

        SdkCall.execute {
            val preferences = SettingsProvider.loadRoutePreferences()
            preferences.setTransportMode(ERouteTransportMode.Car)
            preferences.setAvoidTraffic(true)

            navigationService.startNavigation(
                waypoints, navigationListener, routeCalcListener, preferences
            )
        }
    }
}

// PREDEFINED

open class SimLandmarksController(context: Context, attrs: AttributeSet?) :
    BaseSimulationController(context, attrs) {
    override fun doStart() {
        @Suppress("UNCHECKED_CAST")
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

    private val searchService = SearchService()

    override fun doStart() {
        SdkCall.execute {
            doSearch()
        }
    }

    private fun doSearch() {
        SdkCall.checkCurrentThread()
        searchService.onStarted = {
            showProgress()
        }

        searchService.onCompleted = onCompleted@{ results, gemError, _ ->
            if (gemError == SdkError.Cancel) return@onCompleted

            hideProgress()

            if (gemError != SdkError.NoError) {
                Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
                return@onCompleted
            }

            if (results.isEmpty()) {
                Toast.makeText(context, "No search results!", Toast.LENGTH_SHORT).show()
                return@onCompleted
            }

            doStart(arrayListOf(results[0]))
        }

        searchService.searchAroundPosition(EGenericCategoriesIDs.GasStation)
    }
}

// CUSTOM

class CustomSimController(context: Context, attrs: AttributeSet?) :
    BaseSimulationController(context, attrs) {
    private val landmarks = ArrayList<Landmark>()
    private var picking = false

    override fun onCreated() {
        pickLocation = findViewById(R.id.pickLocation)
        super.onCreated()

        pickLocation.let {
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
                SdkCall.execute { landmark.name = "Intermediate" }
            }

            it.onDestinationPicked = { landmark ->
                pickLocation.visibility = View.GONE
                landmarks.add(landmark)
                SdkCall.execute { doStart(landmarks) }
                picking = false
                TutorialsOpener.onTutorialDestroyed(it)
            }
        }
    }

    override fun doStart() {
        landmarks.clear()

        picking = true
        pickLocation.mapActivity = mapActivity
        pickLocation.pickStart()
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
        pickLocation = findViewById(R.id.pickLocation)
        super.onCreated()

        pickLocation.let {
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
                SdkCall.execute { landmark.name = "Intermediate" }
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
        pickLocation.mapActivity = mapActivity
        pickLocation.pickDestination()
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        if (pickLocation.visibility != View.GONE) return
        if (picking) return

        super.onMapFollowStatusChanged(following)
    }
}
