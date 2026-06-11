/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.Service
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers.SearchTextController.Companion.asSearchModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.ListScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AppProcess
import com.magiclane.sdk.examples.androidautoroutenavigation.services.HistoryInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.services.RoutingInstance
import com.magiclane.sdk.util.SdkCall

typealias HistoryScreen = ListScreen

class HistoryController(context: CarContext) : HistoryScreen(context) {

    override fun updateData() {
        title = context.getString(R.string.history)
        headerAction = UIActionModel.backModel()

        listItemModelList = loadItems()
    }

    private fun loadItems(): ArrayList<GenericListItemModel> {
        val result = ArrayList<GenericListItemModel>()

        SdkCall.execute {
            val reference = AppProcess.currentPosition

            HistoryInstance.trips.sortByDescending { it.timestamp }

            HistoryInstance.trips.forEach {
                val model = asSearchModel(it.waypoints.last(), reference) ?: return@forEach

                model.onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    SdkCall.execute {
                        it.preferences?.let { preferences ->
                            RoutingInstance.avoidFerries = preferences.avoidFerries
                            RoutingInstance.avoidMotorways = preferences.avoidMotorways
                            RoutingInstance.avoidTraffic = preferences.avoidTraffic
                            RoutingInstance.avoidUnpavedRoads = preferences.avoidUnpavedRoads
                            RoutingInstance.avoidTollRoads = preferences.avoidTollRoads
                        }
                    }

                    Service.pushScreen(RoutesPreviewController(context, it.waypoints), true)
                }
                result.add(model)
            }
        }
        return result
    }

    override fun updateMapView() {
    }
}
