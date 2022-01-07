/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.generalmagic.sdk.examples.androidauto.androidAuto.Service
import com.generalmagic.sdk.examples.androidauto.androidAuto.controllers.SearchTextController.Companion.asSearchModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.screens.ListScreen
import com.generalmagic.sdk.examples.androidauto.app.AppProcess
import com.generalmagic.sdk.examples.androidauto.services.HistoryInstance
import com.generalmagic.sdk.examples.androidauto.services.RoutingInstance
import com.generalmagic.sdk.util.SdkCall

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
