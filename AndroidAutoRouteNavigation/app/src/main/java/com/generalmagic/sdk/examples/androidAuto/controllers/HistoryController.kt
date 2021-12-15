/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidAuto.controllers

import androidx.car.app.CarContext
import com.generalmagic.sdk.examples.androidAuto.Service
import com.generalmagic.sdk.examples.androidAuto.controllers.SearchTextController.Companion.asSearchModel
import com.generalmagic.sdk.examples.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.screens.ListScreen
import com.generalmagic.sdk.examples.app.AppProcess
import com.generalmagic.sdk.examples.services.HistoryInstance
import com.generalmagic.sdk.examples.services.RoutingInstance
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

                model.onClicked = {
                    SdkCall.execute {
                        it.preferences?.let { preferences ->
                            RoutingInstance.avoidFerries = preferences.avoidFerries
                            RoutingInstance.avoidMotorways = preferences.avoidMotorways
                            RoutingInstance.avoidTraffic = preferences.avoidTraffic
                            RoutingInstance.avoidUnpavedRoads = preferences.avoidUnpavedRoads
                            RoutingInstance.avoidTollRoads = preferences.avoidTollRoads
                        }
                    }

                    Service.show(RoutesPreviewController(context, it.waypoints))
                }
                result.add(model)
            }
        }
        return result
    }

}
