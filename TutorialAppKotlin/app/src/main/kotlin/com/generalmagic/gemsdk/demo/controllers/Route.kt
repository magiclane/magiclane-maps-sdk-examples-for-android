/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.controllers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.activities.RouteDescriptionActivity.Companion.showRouteDescription
import com.generalmagic.gemsdk.demo.activities.publictransport.PublicTransportRouteDescriptionActivity.Companion.showPTRouteDescription
import com.generalmagic.gemsdk.demo.app.BaseLayoutController
import com.generalmagic.gemsdk.demo.app.MapFollowingStatusProvider
import com.generalmagic.gemsdk.demo.app.StaticsHolder
import com.generalmagic.gemsdk.demo.util.Util
import com.generalmagic.gemsdk.demo.util.UtilUITexts
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Image
import com.generalmagic.gemsdk.models.ImageDatabase
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import kotlinx.android.synthetic.main.pick_location.view.*

abstract class RouteServiceWrapper {
    private val service = RoutingService()
    private val resultRoutes = GEMList(Route::class)

    private val progressListener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) {
            Handler(Looper.getMainLooper()).post {
                onCompleteCalculating(reason)
            }
        }

        override fun notifyStart(hasProgress: Boolean) {
            Handler(Looper.getMainLooper()).post {
                onStartCalculating()
            }
        }
    }

    fun getResults(): ArrayList<Route> {
        GEMSdkCall.checkCurrentThread()

        return resultRoutes.asArrayList()
    }

    fun cancelCalculation() {
        GEMSdkCall.checkCurrentThread()

        service.cancelRoute(progressListener)
    }

    fun calculate(waypoints: ArrayList<Landmark>, preferences: RoutePreferences) {
        GEMSdkCall.checkCurrentThread()

        resultRoutes.assignArrayList(ArrayList())
        service.calculateRoute(resultRoutes, waypoints, preferences, progressListener)
    }

    abstract fun onStartCalculating()
    abstract fun onCompleteCalculating(reason: Int)
}

abstract class BaseUiRouteController(context: Context, attrs: AttributeSet?) :
    BaseLayoutController(context, attrs) {

    private val routing = object : RouteServiceWrapper() {
        override fun onStartCalculating() {
            showProgress()
            updateStartStopBtn(true)
            bottomButtons()?.bottomRightButton?.let { it.visibility = View.INVISIBLE }

            GEMSdkCall.execute {
                StaticsHolder.getMainMapView()?.preferences()?.routes()?.clear()
            }
        }

        override fun onCompleteCalculating(reason: Int) {
            hideProgress()
            updateStartStopBtn(false)

            val gemError = GEMError.fromInt(reason)
            if (gemError != GEMError.KNoError) {
                Toast.makeText(
                    context,
                    "Routing service error: $gemError",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val routes = GEMSdkCall.execute { getResults() } ?: ArrayList()

            val mainRoute: Route? = if (routes.size > MAIN_ROUTE_INDEX) {
                routes[MAIN_ROUTE_INDEX]
            } else {
                null
            }

            GEMSdkCall.execute {
                val mainMap = StaticsHolder.getMainMapView() ?: return@execute

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

                    mainMap.preferences()?.routes()
                        ?.add(routes[routeIndex], routeIndex == MAIN_ROUTE_INDEX, routeName, imageList)
                }
            }

            bottomButtons()?.bottomRightButton?.let {
                if (mainRoute == null) {
                    it.visibility = View.GONE
                    it.parent.requestLayout()
                    return@let
                }

                it.visibility = View.VISIBLE
                it.setOnClickListener {
                    if (mode == TTransportMode.ETM_Public) {
                        showPTRouteDescription(getContext(), mainRoute)
                    } else {
                        showRouteDescription(getContext(), mainRoute)
                    }
                }

                buttonAsDescription(it)
            }
        }
    }

    companion object {
        const val MAIN_ROUTE_INDEX = 0
    }

    protected var mode = TTransportMode.ETM_Car
    private var lastWaypoints = ArrayList<Landmark>()

    override fun onBackPressed(): Boolean {
        bottomButtons()?.bottomRightButton?.let {
            it.visibility = View.GONE
            it.parent.requestLayout()
        }
        return GEMSdkCall.execute { clearRoutes() } ?: false
    }

    fun doStart(waypoints: ArrayList<Landmark>) {
        GEMSdkCall.checkCurrentThread()
        lastWaypoints = waypoints

        clearRoutes()

        val preferences = RoutePreferences()
        preferences.setTransportMode(mode)
        preferences.setRouteType(TRouteType.ERT_Fastest)
        preferences.setAvoidTraffic(true)

        routing.calculate(waypoints, preferences)
    }

    override fun doStop() {
        GEMSdkCall.checkCurrentThread()

        routing.cancelCalculation()
    }

    protected fun updateStartStopBtn(started: Boolean) {
        bottomButtons()?.bottomLeftButton?.let { it ->
            it.visibility = View.VISIBLE
            it.setOnClickListener {
                GEMSdkCall.execute {
                    if (started) doStop()
                    else doStart()
                }
            }

            if (started) buttonAsStop(it)
            else buttonAsStart(it)
        }
    }

    private fun clearRoutes(): Boolean {
        GEMSdkCall.checkCurrentThread()

        val routes = StaticsHolder.getMainMapView()?.preferences()?.routes() ?: return false
        if (routes.size() == 0) {
            return false
        }
        routes.clear()

        return true
    }
}

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------

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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        pickLocation?.let {
            it.onCancelPressed = {
                bottomButtons()?.bottomLeftButton?.visibility = View.GONE
                bottomButtons()?.bottomCenterButton?.visibility = View.GONE
                bottomButtons()?.bottomRightButton?.visibility = View.GONE
                pickLocation.visibility = View.GONE
                GEMSdkCall.execute { MapFollowingStatusProvider.getInstance().doFollowStop() }
            }
            it.onStartPicked = { landmark ->
                landmarks.add(0, landmark)
            }
            it.onIntermediatePicked = { landmark ->
                landmarks.add(landmark)
                landmark.setName("Intermediate")
            }

            it.onDestinationPicked = { landmark ->
                bottomButtons()?.bottomLeftButton?.visibility = View.GONE
                bottomButtons()?.bottomCenterButton?.visibility = View.GONE
                bottomButtons()?.bottomRightButton?.visibility = View.GONE
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
}

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------

class PredefPTNavController(context: Context, attrs: AttributeSet?) :
    BaseUiRouteController(context, attrs) {

    init {
        mode = TTransportMode.ETM_Public
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
        mode = TTransportMode.ETM_Public
    }
}

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------
