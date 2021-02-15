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
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.activities.RouteDescriptionActivity.Companion.showRouteDescription
import com.generalmagic.gemsdk.demo.activities.publictransport.PublicTransportRouteDescriptionActivity.Companion.showPTRouteDescription
import com.generalmagic.gemsdk.demo.activities.routeprofile.GEMRouteProfileView
import com.generalmagic.gemsdk.demo.activities.routeprofile.RouteProfileView
import com.generalmagic.gemsdk.demo.activities.settings.SettingsProvider
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.MapLayoutController
import com.generalmagic.gemsdk.demo.app.Tutorials
import com.generalmagic.gemsdk.demo.app.elements.ButtonsDecorator
import com.generalmagic.gemsdk.demo.util.Util
import com.generalmagic.gemsdk.demo.util.UtilUITexts
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Image
import com.generalmagic.gemsdk.models.ImageDatabase
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.pick_location.view.*

abstract class RouteServiceWrapper {
    private val service = RoutingService()
    private val resultRoutes = GEMList(Route::class)

    private val progressListener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) = GEMApplication.postOnMain {
            onCompleteCalculating(reason)
        }

        override fun notifyStart(hasProgress: Boolean) = GEMApplication.postOnMain {
            onStartCalculating()
        }
    }

    fun getResults(): ArrayList<Route> {
        return GEMSdkCall.execute { resultRoutes.asArrayList() } ?: ArrayList()
    }

    fun cancelCalculation() = GEMSdkCall.execute { service.cancelRoute(progressListener) }

    fun calculate(waypoints: ArrayList<Landmark>, preferences: RoutePreferences) {
        GEMSdkCall.execute {
            resultRoutes.assignArrayList(ArrayList())
            service.calculateRoute(resultRoutes, waypoints, preferences, progressListener)
        }
    }

    abstract fun onStartCalculating()
    abstract fun onCompleteCalculating(reason: Int)
}

abstract class BaseUiRouteController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

    protected var calculatedRoutes = ArrayList<Route>()
    private var mRouteProfileView: RouteProfileView? = null

    private val routing = object : RouteServiceWrapper() {
        override fun onStartCalculating() {
            showProgress()
            updateStartStopBtn(true)
            setInfoButtonVisible(false)

            GEMApplication.clearMapVisibleRoutes()
        }

        override fun onCompleteCalculating(reason: Int) {
            hideProgress()
            updateStartStopBtn(false)

            val gemError = GEMError.fromInt(reason)
            if (gemError != GEMError.KNoError) {
                Toast.makeText(
                    context, "Routing service error: $gemError", Toast.LENGTH_SHORT
                ).show()
                return
            }

            GEMSdkCall.execute {
                val routes = getResults()
                calculatedRoutes = routes

                val mainRoute: Route? = if (routes.size > MAIN_ROUTE_INDEX) {
                    routes[MAIN_ROUTE_INDEX]
                } else {
                    null
                }

                val mainMap = GEMApplication.getMainMapView() ?: return@execute

                if (mainRoute != null) {
                    mainMap.centerOnRoute(mainRoute)
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
                        routes[routeIndex], routeIndex == MAIN_ROUTE_INDEX, routeName, imageList
                    )
                }
            }

            setInfoButtonVisible(true)
            setRouteProfileButtonVisible(true)
        }
    }

    companion object {
        const val MAIN_ROUTE_INDEX = 0
    }

    protected var mode = TRouteTransportMode.ETM_Car
    private var lastWaypoints = ArrayList<Landmark>()

    override fun doBackPressed(): Boolean {
        if (mRouteProfileView == null) {
            setInfoButtonVisible(false)
            return GEMApplication.clearMapVisibleRoutes()
        }
        else {
            GEMSdkCall.execute { 
                GEMRouteProfileView.close()
            }
            return false
        }
    }

    fun doStart(waypoints: ArrayList<Landmark>) {
        if (calculatedRoutes.size > 0) {
            Tutorials.openRouteSimulationTutorial(calculatedRoutes[0])
            return
        }
        lastWaypoints = waypoints

        GEMApplication.clearMapVisibleRoutes()

        GEMSdkCall.execute {
            val preferences = SettingsProvider.loadRoutePreferences()
            preferences.setTransportMode(mode)
            preferences.setAvoidTraffic(true)
            preferences.setBuildTerrainProfile(true)

            routing.calculate(waypoints, preferences)
        }
    }

    override fun doStop() {
        GEMSdkCall.execute { routing.cancelCalculation() }
    }

    protected fun updateStartStopBtn(started: Boolean) {
        if (started) {
            setStopButtonVisible(true)
        } else {
            setStartButtonVisible(true)
        }
    }

    fun doDisplayInfo() {
        val mainRoute = GEMSdkCall.execute {
            val routes = routing.getResults()
            return@execute if (routes.size > MAIN_ROUTE_INDEX) {
                routes[MAIN_ROUTE_INDEX]
            } else null
        } ?: return

        if (mode == TRouteTransportMode.ETM_Public) {
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
                        GEMSdkCall.execute { GEMRouteProfileView.open(mRouteProfileView, mapView) }
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
        doStart(
            arrayListOf(
                Landmark("London", Coordinates(51.5073204, -0.1276475)),
                Landmark("Paris", Coordinates(48.8566932, 2.3514616))
            )
        )
    }
}

class RouteAbc(context: Context, attrs: AttributeSet?) : BaseUiRouteController(context, attrs) {
    override fun doStart() {
        doStart(
            arrayListOf(
                Landmark("Frankfurt am Main", Coordinates(50.11428, 8.68133)),
                Landmark("Karlsruhe", Coordinates(49.0069, 8.4037)),
                Landmark("Munich", Coordinates(48.1351, 11.5820))
            )
        )
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
                landmark.setName("Intermediate")
            }

            it.onDestinationPicked = { landmark ->
                hideAllButtons()
                pickLocation.visibility = View.GONE
                landmarks.add(landmark)

                doStart(landmarks)
            }
        }
    }

    override fun doStart() {
        if (calculatedRoutes.size == 0) {
            landmarks.clear()

            GEMApplication.clearMapVisibleRoutes()

            pickLocation.mapActivity = mapActivity
            pickLocation?.pickStart()
        } else {
            doStart(landmarks)
        }
    }
}

class PredefPTNavController(context: Context, attrs: AttributeSet?) :
    BaseUiRouteController(context, attrs) {

    init {
        mode = TRouteTransportMode.ETM_Public
    }

    override fun doStart() {
        doStart(
            arrayListOf(
                Landmark("San Francisco", Coordinates(37.77903, -122.41991)),
                Landmark("San Jose", Coordinates(37.33619, -121.89058))
            )
        )
    }
}

class CustomPTNavController(context: Context, attrs: AttributeSet?) : RouteCustom(context, attrs) {
    init {
        mode = TRouteTransportMode.ETM_Public
    }
}
