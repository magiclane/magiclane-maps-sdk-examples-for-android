/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import android.util.TypedValue
import androidx.car.app.CarContext
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.Service
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIRouteModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.PreviewRoutesScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.services.RoutingInstance
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
            if (index !in 0 until items.size) {
                return null
            }

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
        },
    )

    constructor(context: CarContext, destination: Landmark) : super(context) {
        this.waypoints = arrayListOf(destination)
    }

    constructor(context: CarContext, waypoints: LandmarkList) : super(context) {
        this.waypoints = waypoints
    }

    override fun onCreate() {
        super.onCreate()
        RoutingInstance.results.clear()
        RoutingInstance.listeners.add(listener)

        isLoading = true

        startCalculating()
    }

    override fun onDestroy() {
        super.onDestroy()
        RoutingInstance.listeners.remove(listener)
    }

    override fun updateData() {
        title = context.getString(R.string.routes)
        noDataText = context.getString(R.string.no_results)
        headerAction = UIActionModel.backModel()

        navigateAction = UIActionModel()
        navigateAction.text = context.getString(R.string.start)
        navigateAction.onClicked = onClicked@{
            if (Service.topScreen != this) {
                return@onClicked
            }

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

    // Converts a dp value to pixels using the current display metrics.
    private fun dpToPixels(dp: Int): Int {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), metrics).toInt()
    }

    override fun updateMapView() {
        if (isLoading) return

        SdkCall.execute {
            val viewport = mapView?.viewport ?: return@execute
            val visibleArea = Service.instance?.surfaceAdapter?.visibleArea ?: return@execute

            // Inset the camera viewport so the route fits within the visible panel area.
            val inflate = dpToPixels(25)
            val left = visibleArea.left + inflate
            val top = visibleArea.top + inflate
            val right = viewport.right - (visibleArea.right - inflate)
            val bottom = viewport.bottom - (visibleArea.bottom - inflate)

            mapView?.presentRoutes(
                RoutingInstance.results,
                routeResultSelected,
                edgeAreaInsets = Rect(left, top, right, bottom),
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
        } else {
            selectedIndex = index
            updateMapView()
            invalidate()
        }
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
            val remainingTime = item.instructions[0].remainingTravelTimeDistance?.totalTime ?: 0

            // Compute ETA by adding remaining seconds to the current local time.
            val arrivalTime = Time().also {
                it.setLocalTime()
                it.longValue += remainingTime * 1000L
            }

            val etaText = String.format("%d:%02d", arrivalTime.hour, arrivalTime.minute)

            return UIRouteModel(
                title = etaText,
                totalDistance = item.timeDistance?.totalDistance ?: 0,
                totalTime = item.timeDistance?.totalTime?.toLong() ?: 0L,
            )
        }
    }
}
