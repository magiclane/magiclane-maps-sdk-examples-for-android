/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIRouteModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.PreviewRoutesScreen
import com.magiclane.sdk.examples.androidauto.services.RoutingInstance
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode.Car
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

class RoutesPreviewController : PreviewRoutesScreen {
    private val waypoints: LandmarkList

    private val routeResultSelected: Route?
        get() {
            val index = selectedIndex
            val items = RoutingInstance.results
            if (index !in 0 until items.size)
                return null

            return items[index]
        }

    private val listener = ProgressListener.create(
        onStarted = {
            isLoading = true

            invalidate()
        },
        onCompleted = { _, _ ->
            isLoading = false
            selectedIndex = 0

            updateMapView()
            invalidate()
        }
    )

    constructor(context: CarContext, destination: Landmark) : super(context) {
        this.waypoints = arrayListOf(destination)
    }

    constructor(context: CarContext, waypoints: LandmarkList) : super(context) {
        this.waypoints = waypoints
    }

    override fun onCreate() {
        super.onCreate()
        RoutingInstance.listeners.add(listener)

        isLoading = true

        startCalculating()
    }

    override fun onDestroy() {
        super.onDestroy()
        RoutingInstance.listeners.remove(listener)
    }

    override fun updateData() {
        title = "Routes"
        noDataText = "No results"
        headerAction = UIActionModel.backModel()

        navigateAction = UIActionModel()
        navigateAction.text = "Continue"
        navigateAction.onClicked = onClicked@{
            if (Service.topScreen != this)
                return@onClicked

            startNavigation()
        }

        itemModelList = getItems()
    }

    override fun onBackPressed() {
        SdkCall.execute {
            RoutingInstance.service.cancelRoute()
        }
        super.onBackPressed()
    }

    override fun updateMapView() {
        SdkCall.execute {
            val viewport = mapView?.viewport ?: return@execute
            val visibleArea = Service.instance?.surfaceAdapter?.visibleArea ?: return@execute

            val left = visibleArea.left
            val top = visibleArea.top
            val right = viewport.right - visibleArea.right
            val bottom = viewport.bottom - visibleArea.bottom

            mapView?.presentRoutes(
                RoutingInstance.results,
                routeResultSelected,
                edgeAreaInsets = Rect(left, top, right, bottom)
            )
        }
    }

    private fun startCalculating() {
        SdkCall.execute {
            val error = RoutingInstance.calculateRoute(waypoints, Car)
            if (GemError.isError(error)) {
                Util.postOnMain {
                    Service.pushScreen(ErrorDialogController(context, error))
                }
            }
        }
    }

    private fun startNavigation() {
        routeResultSelected?.let {
            Service.pushScreen(NavigationController(context, it))
        }
    }

    override fun didSelectItem(index: Int) {
        if (selectedIndex == index) {
            startNavigation()
        }

        selectedIndex = index
        updateMapView()
    }

    private fun getItems(): ArrayList<UIRouteModel> {
        val result = ArrayList<UIRouteModel>()

        SdkCall.execute {
            RoutingInstance.results.forEach {
                result.add(asModel(it))
            }
        }

        return result
    }

    companion object {
        private fun asModel(item: Route): UIRouteModel {
            val navInstr = item.instructions[0]
            val remainingTime = navInstr.remainingTravelTimeDistance?.totalTime ?: 0

            val arrivalTime = Time()

            arrivalTime.setLocalTime()
            arrivalTime.longValue = arrivalTime.longValue + remainingTime * 1000

            val etaText = String.format("%d:%02d", arrivalTime.hour, arrivalTime.minute)
            return UIRouteModel(
                etaText,
                item.timeDistance?.totalDistance ?: 0,
                item.timeDistance?.totalTime?.toLong() ?: 0L
            )
        }
    }

}
