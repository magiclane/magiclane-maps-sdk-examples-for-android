/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.controllers.SearchTextController.Companion.asSearchModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.ListScreen
import com.magiclane.sdk.examples.androidauto.app.AppProcess
import com.magiclane.sdk.examples.androidauto.services.HistoryInstance
import com.magiclane.sdk.examples.androidauto.services.RoutingInstance
import com.magiclane.sdk.util.SdkCall

typealias HistoryScreen = ListScreen

class HistoryController(context: CarContext) : HistoryScreen(context) {

    override fun updateData() {
        title = "History"
        headerAction = UIActionModel.backModel()

        listItemModelList = loadItems()
    }

    private fun loadItems(): ArrayList<GenericListItemModel> {
        val result = ArrayList<GenericListItemModel>()

        SdkCall.execute {
            val reference = AppProcess.currentPosition
            HistoryInstance.trips.forEach {
                val model = asSearchModel(it.waypoints.last(), reference) ?: return@forEach

                model.onClicked = onClicked@{
                    if (Service.topScreen != this)
                        return@onClicked

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

}
