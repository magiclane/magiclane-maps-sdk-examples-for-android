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
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.examples.demo.activities.RouteDescriptionActivity.Companion.showRouteDescription
import com.generalmagic.sdk.examples.demo.activities.publictransport.PublicTransportRouteDescriptionActivity.Companion.showPTRouteDescription
import com.generalmagic.sdk.examples.demo.activities.routeprofile.GEMRouteProfileView
import com.generalmagic.sdk.examples.demo.activities.routeprofile.RouteProfileView
import com.generalmagic.sdk.examples.demo.activities.settings.SettingsProvider
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.MapLayoutController
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.examples.demo.app.TutorialsOpener
import com.generalmagic.sdk.examples.demo.app.elements.ButtonsDecorator
import com.generalmagic.sdk.examples.demo.util.IntentHelper
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.UtilUITexts
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.ERouteTransportMode
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import kotlinx.android.synthetic.main.pick_location.view.*

abstract class BaseUiRouteController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

    protected var calculatedRoutes = ArrayList<Route>()
    private var mRouteProfileView: RouteProfileView? = null

    private val routingService = RoutingService()

    companion object {
        const val MAIN_ROUTE_INDEX = 0
    }

    protected var mode = ERouteTransportMode.Car
    private var lastWaypoints = ArrayList<Landmark>()

    override fun doBackPressed(): Boolean {
        if (mRouteProfileView == null) {
            setInfoButtonVisible(false)
            return GEMApplication.clearMapVisibleRoutes()
        } else {
            SdkCall.execute {
                GEMRouteProfileView.close()
            }
            return false
        }
    }

    fun doStart(waypoints: ArrayList<Landmark>) {
        routingService.onStarted = {
            showProgress()
            updateStartStopBtn(true)
            setInfoButtonVisible(false)

            GEMApplication.clearMapVisibleRoutes()
        }

        routingService.onCompleted = onCompleted@{ routes, reason, hint ->
            hideProgress()
            updateStartStopBtn(false)
            calculatedRoutes = routes

            val gemError = SdkError.fromInt(reason)
            if (gemError != SdkError.NoError) {
                Toast.makeText(
                    context, "Routing service error: $gemError", Toast.LENGTH_SHORT
                ).show()
                return@onCompleted
            }

            SdkCall.execute {
                val mainRoute: Route? = if (routes.size > MAIN_ROUTE_INDEX) {
                    routes[MAIN_ROUTE_INDEX]
                } else {
                    null
                }

                val mainMap = GEMApplication.getMainMapView() ?: return@execute

                if (mainRoute != null) {
                    mainMap.centerOnRoute(mainRoute)
                    GEMApplication.addRouteToHistory(mainRoute)
                }

                for (routeIndex in 0 until routes.size) {
                    val route = routes[routeIndex]

                    val routeName = UtilUITexts.formatRouteName(route)

                    val imageList = arrayListOf<Image>()
                    ImageDatabase().getImageById(Util.getTrafficIconId(route))?.let {
                        imageList.add(it)
                    }

                    if (route.hasTollRoads()) {
                        ImageDatabase().getImageById(Util.getTollIconId())?.let {
                            imageList.add(it)
                        }
                    }

                    if (route.hasFerryConnections()) {
                        ImageDatabase().getImageById(Util.getFerryIconId())?.let {
                            imageList.add(it)
                        }
                    }

                    mainMap.preferences()?.routes()?.add(
                        route, routeIndex == MAIN_ROUTE_INDEX, routeName, imageList
                    )
                }
            }

            setInfoButtonVisible(true)
            setRouteProfileButtonVisible(true)
        }

        if (calculatedRoutes.size > 0) {
            Tutorials.openRouteSimulationTutorial(calculatedRoutes[0])
            return
        }
        lastWaypoints = waypoints

        GEMApplication.clearMapVisibleRoutes()

        SdkCall.execute {
            val savedPrefs = SettingsProvider.loadRoutePreferences()

            routingService.preferences.setRouteType(savedPrefs.getRouteType())
            routingService.preferences.setAvoidTollRoads(savedPrefs.getAvoidTollRoads())
            routingService.preferences.setAvoidMotorways(savedPrefs.getAvoidMotorways())
            routingService.preferences.setAvoidFerries(savedPrefs.getAvoidFerries())
            routingService.preferences.setAvoidUnpavedRoads(savedPrefs.getAvoidUnpavedRoads())

            routingService.preferences.setTransportMode(mode)
            routingService.preferences.setAvoidTraffic(true)
            routingService.preferences.setBuildTerrainProfile(true)
            val time = Time()
            time.setLocalTime()
            routingService.preferences.setTimestamp(time)

            routingService.calculateRoute(waypoints)
        }
    }

    override fun doStop() {
        SdkCall.execute { routingService.cancelRoute() }
    }

    protected fun updateStartStopBtn(started: Boolean) {
        if (started) {
            setStopButtonVisible(true)
        } else {
            setStartButtonVisible(true)
        }
    }

    fun doDisplayInfo() {
        val mainRoute = SdkCall.execute {
            val routes = calculatedRoutes
            return@execute if (routes.size > MAIN_ROUTE_INDEX) {
                routes[MAIN_ROUTE_INDEX]
            } else null
        } ?: return

        if (mode == ERouteTransportMode.Public) {
            showPTRouteDescription(context, mainRoute)
        } else {
            showRouteDescription(context, mainRoute)
        }
    }

    fun setInfoButtonVisible(visible: Boolean) {
        val button = getBottomRightButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsInfo(context, button) {
                doDisplayInfo()
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    fun setRouteProfileButtonVisible(visible: Boolean) {

        getBottomCenterButton()?.let { button ->
            if (visible) {
                ButtonsDecorator.buttonAsRouteProfile(context, button) {
                    mRouteProfileView = RouteProfileView(mapActivity)
                    GEMApplication.getMainMapView()?.let { mapView ->
                        SdkCall.execute { GEMRouteProfileView.open(mRouteProfileView, mapView) }
                    }
                }

                button.visibility = View.VISIBLE
            } else {
                button.visibility = View.GONE
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        mRouteProfileView?.run {
            val orientation = newConfig?.orientation ?: Configuration.ORIENTATION_PORTRAIT
            adjustViewForOrientation(orientation)
        }
    }

    fun onRouteProfileViewIsClosed() {
        mRouteProfileView = null
    }
}

class RouteAb(context: Context, attrs: AttributeSet?) : BaseUiRouteController(context, attrs) {
    override fun doStart() {
        val waypoints = SdkCall.execute {
            arrayListOf(
                Landmark("London", Coordinates(51.5073204, -0.1276475)),
                Landmark("Paris", Coordinates(48.8566932, 2.3514616))
            )
        } ?: return
        doStart(waypoints)
    }
}

class RouteAbc(context: Context, attrs: AttributeSet?) : BaseUiRouteController(context, attrs) {
    override fun doStart() {
        val waypoints = SdkCall.execute {
            arrayListOf(
                Landmark("Frankfurt am Main", Coordinates(50.11428, 8.68133)),
                Landmark("Karlsruhe", Coordinates(49.0069, 8.4037)),
                Landmark("Munich", Coordinates(48.1351, 11.5820))
            )
        } ?: return

        doStart(waypoints)
    }
}

open class RouteCustom(context: Context, attrs: AttributeSet?) :
    BaseUiRouteController(context, attrs) {
    private val landmarks = ArrayList<Landmark>()

    override fun onMapFollowStatusChanged(following: Boolean) {}

    override fun onCreated() {
        super.onCreated()

        pickLocation?.let {
            it.onCancelPressed = {
                pickLocation.visibility = View.GONE
                GEMApplication.doMapFollow(false)
                Tutorials.openHelloWorldTutorial()
            }
            it.onStartPicked = { landmark ->
                landmarks.add(0, landmark)
            }
            it.onIntermediatePicked = { landmark ->
                landmarks.add(landmark)
                SdkCall.execute { landmark.setName("Intermediate") }
            }

            it.onDestinationPicked = { landmark ->
                hideAllButtons()
                pickLocation.visibility = View.GONE
                landmarks.add(landmark)
                TutorialsOpener.onTutorialDestroyed(it)
                doStart(landmarks)
            }
        }
    }

    override fun doStart() {
        if (calculatedRoutes.size == 0) {
            landmarks.clear()

            GEMApplication.clearMapVisibleRoutes()
            pickLocation.mapActivity = mapActivity

            val waypoints = IntentHelper.getObjectForKey(EXTRA_WAYPOINTS) as ArrayList<Landmark>?

            if (waypoints != null && waypoints.isNotEmpty()) {
                doStart(waypoints)
            } else {
                pickLocation?.pickStart()
            }
        } else {
            doStart(landmarks)
        }
    }

    companion object {
        const val EXTRA_WAYPOINTS = "waypoints"
    }
}

class PredefPTNavController(context: Context, attrs: AttributeSet?) :
    BaseUiRouteController(context, attrs) {

    init {
        mode = ERouteTransportMode.Public
    }

    override fun doStart() {
        val waypoints = SdkCall.execute {
            arrayListOf(
                Landmark("San Francisco", Coordinates(37.77903, -122.41991)),
                Landmark("San Jose", Coordinates(37.33619, -121.89058))
            )
        } ?: return
        doStart(waypoints)
    }
}

class CustomPTNavController(context: Context, attrs: AttributeSet?) : RouteCustom(context, attrs) {
    init {
        mode = ERouteTransportMode.Public
    }
}
