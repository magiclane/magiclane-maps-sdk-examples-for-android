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
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.MapLayoutController
import com.generalmagic.gemsdk.demo.app.Tutorials
import com.generalmagic.gemsdk.demo.app.TutorialsOpener
import com.generalmagic.gemsdk.demo.app.elements.ButtonsDecorator
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.SearchPreferences
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.location_details_panel.view.*
import kotlinx.android.synthetic.main.nav_top_panel.view.*

abstract class FlyController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

}

class FlyToCoords(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    override fun doStart() {
        val mainMap = GEMApplication.getMainMapView() ?: return

        Tutorials.openHelloWorldTutorial()

        GEMSdkCall.execute {
            val coordinates = Coordinates(45.651178, 25.604872)

            val animation = Animation()
            animation.setMethod(TAnimation.EAnimationFly)

            mainMap.centerOnCoordinates(coordinates, -3, TXy(), animation)
        }
    }
}

class FlyToArea(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    private val preferences = SearchPreferences()
    private val search = object : SearchServiceWrapper() {
        override fun onSearchStarted() {
            showProgress()

            hideAllButtons()
            setCustomStopButtonVisible(true)
        }

        override fun onSearchCompleted(reason: Int) {
            hideProgress()

            hideAllButtons()
            setCustomStartButtonVisible(true)

            val gemError = GEMError.fromInt(reason)
            if (gemError == GEMError.KCancel) return

            if (gemError != GEMError.KNoError) {
                Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
                return
            }

            val results = GEMSdkCall.execute { this.results.asArrayList() }
            if (results == null || results.size == 0) return

            val mainMap = GEMApplication.getMainMapView() ?: return

            val value = results[0]
            val area = GEMSdkCall.execute { value.getContourGeograficArea() } ?: return

            wikiView.onWikiFetchCompleteCallback = { _: Int, _: String ->
                GEMApplication.postOnMain {
                    hideProgress()
                }
            }

//            showProgress()
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
        GEMSdkCall.execute {
            val text = "Statue of Liberty New York"
            val coords = Coordinates(40.68925476, -74.04456329)
            doSearch(text, coords)
        }
    }

    override fun doStop() {
        GEMSdkCall.execute {
            search.service.cancelRequest(search.listener)
        }
    }

    private fun doSearch(text: String, coords: Coordinates) {
        GEMSdkCall.execute {
            preferences.setReferencePoint(coords)
            preferences.setSearchMapPOIs(true)
            search.service.searchByFilter(search.results, search.listener, text, preferences)
        }
    }

    fun setCustomStartButtonVisible(visible: Boolean) {
        val button = closeButton ?: return

        if (visible) {
            ButtonsDecorator.buttonAsStart(context, button) {
                doStart()
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    fun setCustomStopButtonVisible(visible: Boolean) {
        val button = closeButton ?: return

        if (visible) {
            ButtonsDecorator.buttonAsStop(context, button) {
                doStart()
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }
}

open class FlyToInstr(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    protected var isFlyToRoute = false
    private val routing = object : RouteServiceWrapper() {
        override fun onStartCalculating() {
            showProgress()
            hideAllButtons()
            setStopButtonVisible(true)

            GEMApplication.clearMapVisibleRoutes()
        }

        override fun onCompleteCalculating(reason: Int) {
            hideProgress()
            setStartButtonVisible(true)

            val gemError = GEMError.fromInt(reason)
            if (gemError != GEMError.KNoError) {
                Toast.makeText(
                    context,
                    "Routing service error: $gemError",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            GEMSdkCall.execute {
                val mainMap = GEMApplication.getMainMapView() ?: return@execute

                var selectedRoute: Route? = null
                val segment = GEMSdkCall.execute segment@{
                    val resultsList = getResults()
                    if (resultsList.size == 0) return@segment null
                    val mSelectedRoute = resultsList[0]

                    val segmentsList = mSelectedRoute.getSegments() ?: return@segment null
                    if (segmentsList.size == 0) return@segment null

                    selectedRoute = mSelectedRoute
                    return@segment segmentsList[0]
                }

                val route = selectedRoute
                if (route == null || segment == null) {
                    GEMApplication.postOnMain {
                        Toast.makeText(context, "No route !", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                if (isFlyToRoute) {
                    val animation = Animation()
                    animation.setMethod(TAnimation.EAnimationFly)

                    mainMap.preferences()?.routes()?.add(route, true)

                    mainMap.centerOnRoute(route, TRect(), animation)
                } else {
                    val instructionsList = segment.getInstructions() ?: return@execute
                    val instruction =
                        if (instructionsList.size > 2) instructionsList[2] else return@execute

                    GEMApplication.postOnMain {
                        GEMApplication.setAppBarVisible(false)
                        GEMApplication.setSystemBarsVisible(false)

                        topPanelController.update(instruction)
                    }

                    mainMap.preferences()?.routes()?.add(route, true)
                    GEMApplication.focusOnRouteInstructionItem(instruction)
                }
            }
        }
    }

    override fun doStart() {
        GEMSdkCall.execute {
            val from = Landmark("Gheorghe Lazar", Coordinates(45.65131, 25.60493))
            val to = Landmark("Bulevardul Grivitei", Coordinates(45.65272, 25.60674))

            val preferences = RoutePreferences()
            preferences.setTransportMode(TRouteTransportMode.ETM_Car)
            preferences.setRouteType(TRouteType.ERT_Fastest)
            preferences.setAvoidTraffic(true)

            routing.calculate(arrayListOf(from, to), preferences)
        }
    }

    override fun doStop() {
        GEMSdkCall.execute { routing.cancelCalculation() }
    }

    override fun doBackPressed(): Boolean {
        GEMApplication.setAppBarVisible(true)
        GEMApplication.setSystemBarsVisible(true)
        hideProgress()
        return true
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
            hideAllButtons()
            setStopButtonVisible(true)

            GEMApplication.clearMapVisibleRoutes()
        }

        override fun onCompleteCalculating(reason: Int) {
            hideProgress()
            GEMApplication.setAppBarVisible(false)
            GEMApplication.setSystemBarsVisible(false)
            hideAllButtons()
            setStartButtonVisible(true)

            val gemError = GEMError.fromInt(reason)
            if (gemError != GEMError.KNoError) {
                Toast.makeText(
                    context,
                    "Routing service error: $gemError",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            GEMSdkCall.execute {
                val mainMap = GEMApplication.getMainMapView() ?: return@execute

                var selectedRoute: Route? = null
                val trafficEvent = GEMSdkCall.execute trafficEvent@{
                    val resultsList = getResults()
                    if (resultsList.size == 0) return@trafficEvent null
                    val mSelectedRoute = resultsList[0]

                    val trafficEventsLists = mSelectedRoute.getTrafficEvents() ?: ArrayList()
                    if (trafficEventsLists.size == 0) {
                        return@trafficEvent null
                    }

                    selectedRoute = mSelectedRoute
                    return@trafficEvent trafficEventsLists[0]
                }

                val route = selectedRoute
                if (trafficEvent == null || route == null) {
                    GEMApplication.postOnMain {
                        Toast.makeText(context, "No traffic Events!", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val totalRouteLength = route.getTimeDistance()?.getTotalDistance() ?: 0

                GEMApplication.postOnMain {
                    topPanelController.update(trafficEvent, totalRouteLength)
                }

                mainMap.preferences()?.routes()?.add(route, true)
                GEMApplication.focusOnRouteTrafficItem(trafficEvent)
            }
        }
    }

    override fun doStart() {
        GEMSdkCall.execute {
            val from = Landmark("London", Coordinates(51.50732, -0.12765))
            val to = Landmark("Maidstone", Coordinates(51.27483, 0.52316))

            val preferences = RoutePreferences()
            preferences.setTransportMode(TRouteTransportMode.ETM_Car)
            preferences.setRouteType(TRouteType.ERT_Fastest)
            preferences.setAvoidTraffic(true)

            routing.calculate(arrayListOf(from, to), preferences)
        }
    }

    override fun doStop() {
        GEMSdkCall.execute { routing.cancelCalculation() }
    }

    override fun doBackPressed(): Boolean {
        GEMApplication.setAppBarVisible(true)
        GEMApplication.setSystemBarsVisible(true)
        hideProgress()
        return true
    }
}

class FlyToLine(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    override fun onCreated() {
        TutorialsOpener.onTutorialCreated(this)
        hideAllButtons()
        setStopButtonVisible(true) 
    }
    
    override fun doStart() {
        val mainMap = GEMApplication.getMainMapView() ?: return
        
        GEMSdkCall.execute {
            val vector = VectorDataSource(TVectorDataType.EVDT_Polyline.value, "My polylines data source")
            val vectorItem = VectorItem()
            
            vectorItem.add(Coordinates(52.360234, 4.886782))
            vectorItem.add(Coordinates(52.360495, 4.886266))
            vectorItem.add(Coordinates(52.360854, 4.885539))
            vectorItem.add(Coordinates(52.361184, 4.884849))
            vectorItem.add(Coordinates(52.361439, 4.884344))
            vectorItem.add(Coordinates(52.361593, 4.883986))
            
            vector.add(vectorItem)
            val area = vector.getArea()
            
            mainMap.preferences()?.vectors()?.add(vector)
            val animation = Animation()
            animation.setMethod(TAnimation.EAnimationFly)

            if (area != null) {
                mainMap.centerOnArea(area, -1, TXy(), animation)
            }
        }
    }

    override fun doStop() {
        doBackPressed()
    }

    override fun doBackPressed(): Boolean {
        val mainMap = GEMApplication.getMainMapView() ?: return false
        GEMSdkCall.execute { mainMap.preferences()?.vectors()?.clear() }
        setFollowGpsButtonVisible(true)
        setStopButtonVisible(false)
        return true
    }
}
