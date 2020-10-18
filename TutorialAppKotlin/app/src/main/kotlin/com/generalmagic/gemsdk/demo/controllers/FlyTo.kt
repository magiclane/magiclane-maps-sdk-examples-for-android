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
import com.generalmagic.gemsdk.demo.app.BaseLayoutController
import com.generalmagic.gemsdk.demo.app.StaticsHolder
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.SearchPreferences
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import kotlinx.android.synthetic.main.location_details_panel.view.*
import kotlinx.android.synthetic.main.nav_top_panel.view.*

abstract class FlyController(context: Context, attrs: AttributeSet?) :
    BaseLayoutController(context, attrs) {

    protected fun updateStartStopBtn(started: Boolean) {
        bottomButtons()?.let { buttons ->
            buttons.bottomLeftButton?.let {
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
    }
}

class FlyToCoords(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    override fun doStart() {
        GEMSdkCall.checkCurrentThread()
        val mainMap = StaticsHolder.getMainMapView() ?: return

        val coordinates = Coordinates(45.651178, 25.604872)

        val animation = Animation()
        animation.setMethod(TAnimation.EAnimationFly)

        mainMap.centerOnCoordinates(coordinates, -3, TXy(), animation)
    }
}

class FlyToArea(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    private val preferences = SearchPreferences()
    private val search = object : SearchServiceWrapper() {
        override fun onSearchStarted() {
            showProgress()

            updateStartStopBtn(true)
        }

        override fun onSearchCompleted(reason: Int) {
            val gemError = GEMError.fromInt(reason)
            if (gemError == GEMError.KCancel) return

            hideProgress()
            updateStartStopBtn(false)

            if (gemError != GEMError.KNoError) {
                Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
                return
            }

            val results = GEMSdkCall.execute { this.results.asArrayList() }
            if (results == null || results.size == 0) return

            val mainMap = StaticsHolder.getMainMapView() ?: return

            val value = results[0]
            val area = GEMSdkCall.execute { value.getContourGeograficArea() } ?: return

            wikiView.onWikiFetchCompleteCallback = { i: Int, s: String ->
                Handler(Looper.getMainLooper()).post {
                    hideProgress()
                }
            }

            showProgress()
            if (!wikiView.show(value)) {
                hideProgress()
            }

            GEMSdkCall.execute {
                val animation = Animation()
                animation.setMethod(TAnimation.EAnimationFly)

                val settings = HighlightRenderSettings()
                settings.setOptions(
                    if (area.isEmpty()) {
                        THighlightOptions.EHO_ShowLandmark.value
                    } else {
                        THighlightOptions.EHO_ShowContour.value
                    }
                )

                mainMap.centerOnRectArea(area, -3, TRect(), animation)
                mainMap.activateHighlightLandmarks(arrayListOf(value), settings)
            }
        }
    }

    override fun doStart() {
        GEMSdkCall.checkCurrentThread()

        val text = "Statue of Liberty New York"
        val coords = Coordinates(40.68925476, -74.04456329)
        doSearch(text, coords)
    }

    override fun doStop() {
        GEMSdkCall.checkCurrentThread()

        search.service.cancelRequest(search.listener)
    }

    private fun doSearch(text: String, coords: Coordinates) {
        GEMSdkCall.checkCurrentThread()
        preferences.setReferencePoint(coords)
        preferences.setSearchMapPOIs(true)
        search.service.searchByFilter(search.results, search.listener, text, preferences)
    }
}

open class FlyToInstr(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    protected var isFlyToRoute = false
    private val routing = object : RouteServiceWrapper() {
        override fun onStartCalculating() {
            showProgress()
            updateStartStopBtn(true)

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

            val mainMap = StaticsHolder.getMainMapView() ?: return

            var selectedRoute: Route? = null
            val segment = GEMSdkCall.execute {
                val resultsList = getResults()
                if (resultsList.size == 0) return@execute null
                val mSelectedRoute = resultsList[0]

                val segmentsList = mSelectedRoute.getSegments() ?: return@execute null
                if (segmentsList.size == 0) return@execute null

                selectedRoute = mSelectedRoute
                return@execute segmentsList[0]
            }

            val route = selectedRoute
            if (route == null || segment == null) {
                Toast.makeText(context, "No route !", Toast.LENGTH_SHORT).show()
                return
            }

            if (isFlyToRoute) {
                GEMSdkCall.execute {
                    val animation = Animation()
                    animation.setMethod(TAnimation.EAnimationFly)

                    mainMap.preferences()?.routes()?.add(route, true)

                    mainMap.centerOnRoute(route, TRect(), animation)
                }
            } else {
                val instructionsList = GEMSdkCall.execute { segment.getInstructions() } ?: return
                val instruction = if (instructionsList.size > 2) instructionsList[2] else return

                topPanelController.update(instruction)

                GEMSdkCall.execute {
                    val animation = Animation()
                    animation.setMethod(TAnimation.EAnimationFly)

                    mainMap.preferences()?.routes()?.add(route, true)

                    mainMap.centerOnRouteInstruction(instruction, -1, TXy(), animation)
                }
            }
        }
    }

    override fun doStart() {
        GEMSdkCall.checkCurrentThread()

        val from = Landmark("Gheorghe Lazar", Coordinates(45.65131, 25.60493))
        val to = Landmark("Bulevardul Grivitei", Coordinates(45.65272, 25.60674))

        val preferences = RoutePreferences()
        preferences.setTransportMode(TTransportMode.ETM_Car)
        preferences.setRouteType(TRouteType.ERT_Fastest)
        preferences.setAvoidTraffic(true)

        routing.calculate(arrayListOf(from, to), preferences)
    }

    override fun doStop() {
        GEMSdkCall.checkCurrentThread()

        routing.cancelCalculation()
    }
}

class FlyToRoute(context: Context, attrs: AttributeSet?) : FlyToInstr(context, attrs) {
    init {
        isFlyToRoute = true
    }
}

class FlyToTraffic(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    private val routing = object : RouteServiceWrapper() {
        override fun onStartCalculating() {
            showProgress()
            updateStartStopBtn(true)

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

            val mainMap = StaticsHolder.getMainMapView() ?: return

            var selectedRoute: Route? = null
            val trafficEvent = GEMSdkCall.execute {
                val resultsList = getResults()
                if (resultsList.size == 0) return@execute null
                val mSelectedRoute = resultsList[0]

                val trafficEventsLists = mSelectedRoute.getTrafficEvents() ?: ArrayList()
                if (trafficEventsLists.size == 0) {
                    return@execute null
                }

                selectedRoute = mSelectedRoute
                return@execute trafficEventsLists[0]
            }

            val route = selectedRoute
            if (trafficEvent == null || route == null) {
                Toast.makeText(context, "No traffic Events!", Toast.LENGTH_SHORT).show()
                return
            }

            val totalRouteLength =
                GEMSdkCall.execute { (route.getTimeDistance()?.getTotalDistance()) } ?: 0

            topPanelController.update(trafficEvent, totalRouteLength)

            GEMSdkCall.execute {
                val animation = Animation()
                animation.setMethod(TAnimation.EAnimationFly)

                mainMap.preferences()?.routes()?.add(route, true)

                mainMap.centerOnRouteTrafficEvent(trafficEvent, -1, TRect(), animation)
            }
        }
    }

    override fun doStart() {
        GEMSdkCall.checkCurrentThread()

        val from = Landmark("London", Coordinates(51.50732, -0.12765))
        val to = Landmark("Maidstone", Coordinates(51.27483, 0.52316))

        val preferences = RoutePreferences()
        preferences.setTransportMode(TTransportMode.ETM_Car)
        preferences.setRouteType(TRouteType.ERT_Fastest)
        preferences.setAvoidTraffic(true)

        routing.calculate(arrayListOf(from, to), preferences)
    }

    override fun doStop() {
        GEMSdkCall.checkCurrentThread()

        routing.cancelCalculation()
    }
}
