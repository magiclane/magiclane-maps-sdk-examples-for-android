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
import com.generalmagic.sdk.core.Rect
import com.generalmagic.sdk.core.Xy
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.d3scene.*
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.MapLayoutController
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.examples.demo.app.TutorialsOpener
import com.generalmagic.sdk.examples.demo.app.elements.ButtonsDecorator
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.util.SdkCall
import kotlinx.android.synthetic.main.location_details_panel.view.*
import kotlinx.android.synthetic.main.nav_top_panel.view.*

abstract class FlyController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs)

class FlyToCoords(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    override fun doStart() {
        val mainMap = GEMApplication.getMainMapView() ?: return

        Tutorials.openHelloWorldTutorial()

        SdkCall.execute {
            val coordinates = Coordinates(45.651178, 25.604872)

            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)

            mainMap.centerOnCoordinates(coordinates, -3, Xy(), animation)
        }
    }
}

class FlyToArea(context: Context, attrs: AttributeSet?) : FlyController(context, attrs) {
    private val searchService = SearchService()

    override fun doStart() {
        SdkCall.execute {
            val text = "Statue of Liberty New York"
            val coords = Coordinates(40.68925476, -74.04456329)
            doSearch(text, coords)
        }
    }

    override fun doStop() {
        SdkCall.execute {
            searchService.cancelSearch()
        }
    }

    @Suppress("SameParameterValue")
    private fun doSearch(text: String, coords: Coordinates) {
        SdkCall.execute {
            searchService.onStarted = {
                showProgress()

                hideAllButtons()
                setCustomStopButtonVisible(true)
            }

            searchService.onCompleted = onCompleted@{ results, gemError, _ ->
                hideProgress()

                hideAllButtons()
                setCustomStartButtonVisible(true)

                if (gemError == SdkError.Cancel) return@onCompleted

                if (gemError != SdkError.NoError) {
                    Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
                    return@onCompleted
                }

                if (results.size == 0) return@onCompleted

                val mainMap = GEMApplication.getMainMapView() ?: return@onCompleted

                val value = results[0]
                val area = SdkCall.execute { value.getContourGeograficArea() } ?: return@onCompleted

                wikiView.onWikiFetchCompleteCallback = { _, _ ->
                    GEMApplication.postOnMain {
                        hideProgress()
                    }
                }

//            showProgress()
                if (!wikiView.show(value)) {
                    hideProgress()
                }

                SdkCall.execute {
                    val animation = Animation()
                    animation.setType(EAnimation.AnimationLinear)

                    val settings = HighlightRenderSettings()
                    settings.setOptions(
                        if (area.isEmpty()) {
                            EHighlightOptions.ShowLandmark.value
                        } else {
                            EHighlightOptions.ShowContour.value
                        }
                    )

                    mainMap.centerOnRectArea(area, -3, Rect(), animation)
                    mainMap.activateHighlightLandmarks(arrayListOf(value), settings)
                }
            }

            searchService.preferences.setSearchMapPOIs(true)

            searchService.searchByFilter(text, coords)
        }
    }

    @Suppress("SameParameterValue")
    private fun setCustomStartButtonVisible(visible: Boolean) {
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

    @Suppress("SameParameterValue")
    private fun setCustomStopButtonVisible(visible: Boolean) {
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
    private val routingService = RoutingService()

    override fun doStart() {
        routingService.onStarted = {
            showProgress()
            hideAllButtons()
            setStopButtonVisible(true)

            GEMApplication.clearMapVisibleRoutes()
        }

        routingService.onCompleted = onCompleted@{ resultsList, gemError, _ ->
            hideProgress()
            setStartButtonVisible(true)

            if (gemError != SdkError.NoError) {
                Toast.makeText(
                    context,
                    "Routing service error: $gemError",
                    Toast.LENGTH_SHORT
                ).show()
                return@onCompleted
            }

            SdkCall.execute {
                val mainMap = GEMApplication.getMainMapView() ?: return@execute

                var selectedRoute: Route? = null
                val segment = SdkCall.execute segment@{
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
                    animation.setType(EAnimation.AnimationLinear)

                    mainMap.preferences()?.routes()?.add(route, true)

                    mainMap.centerOnRoute(route, Rect(), animation)
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

        SdkCall.execute {
            val from = Landmark("Gheorghe Lazar", Coordinates(45.65131, 25.60493))
            val to = Landmark("Bulevardul Grivitei", Coordinates(45.65272, 25.60674))

            routingService.preferences.setTransportMode(ERouteTransportMode.Car)
            routingService.preferences.setRouteType(ERouteType.Fastest)
            routingService.preferences.setAvoidTraffic(true)

            routingService.calculateRoute(arrayListOf(from, to))
        }
    }

    override fun doStop() {
        SdkCall.execute { routingService.cancelRoute() }
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
    private val routingService = RoutingService()

    override fun doStart() {
        routingService.onStarted = {
            showProgress()
            hideAllButtons()
            setStopButtonVisible(true)

            GEMApplication.clearMapVisibleRoutes()
        }

        routingService.onCompleted = onCompleted@{ resultsList, gemError, _ ->
            hideProgress()
            GEMApplication.setAppBarVisible(false)
            GEMApplication.setSystemBarsVisible(false)
            hideAllButtons()
            setStartButtonVisible(true)

            if (gemError != SdkError.NoError) {
                Toast.makeText(
                    context,
                    "Routing service error: $gemError",
                    Toast.LENGTH_SHORT
                ).show()
                return@onCompleted
            }

            SdkCall.execute {
                val mainMap = GEMApplication.getMainMapView() ?: return@execute

                var selectedRoute: Route? = null
                val trafficEvent = SdkCall.execute trafficEvent@{
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

        SdkCall.execute {
            val from = Landmark("London", Coordinates(51.50732, -0.12765))
            val to = Landmark("Maidstone", Coordinates(51.27483, 0.52316))

            routingService.preferences.setTransportMode(ERouteTransportMode.Car)
            routingService.preferences.setRouteType(ERouteType.Fastest)
            routingService.preferences.setAvoidTraffic(true)

            routingService.calculateRoute(arrayListOf(from, to))
        }
    }

    override fun doStop() {
        SdkCall.execute { routingService.cancelRoute() }
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

        SdkCall.execute {
            val vector =
                MarkerCollection(EMarkerType.Polyline.value, "My polylines data source")
            val marker = Marker()

            marker.add(Coordinates(52.360234, 4.886782))
            marker.add(Coordinates(52.360495, 4.886266))
            marker.add(Coordinates(52.360854, 4.885539))
            marker.add(Coordinates(52.361184, 4.884849))
            marker.add(Coordinates(52.361439, 4.884344))
            marker.add(Coordinates(52.361593, 4.883986))

            vector.add(marker)
            val area = vector.getArea()

            mainMap.preferences()?.markers()?.add(vector)
            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)

            if (area != null) {
                mainMap.centerOnArea(area, -1, Xy(), animation)
            }
        }
    }

    override fun doStop() {
        doBackPressed()
    }

    override fun doBackPressed(): Boolean {
        val mainMap = GEMApplication.getMainMapView() ?: return false
        SdkCall.execute { mainMap.preferences()?.markers()?.clear() }
        setFollowGpsButtonVisible(true)
        setStopButtonVisible(false)
        return true
    }
}
