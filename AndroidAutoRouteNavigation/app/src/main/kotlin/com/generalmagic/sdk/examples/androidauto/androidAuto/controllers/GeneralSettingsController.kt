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
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.screens.ListScreen
import com.generalmagic.sdk.examples.androidauto.services.RoutingInstance
import com.generalmagic.sdk.util.SdkCall

typealias GeneralSettingsScreen = ListScreen

class GeneralSettingsController(context: CarContext) : GeneralSettingsScreen(context) {

    override fun updateData() {
        title = "Settings"
        headerAction = UIActionModel.backModel()

        listItemModelList = ArrayList()
        listItemModelList.add(GenericListItemModel(
            title = "Travel Mode",
            isBrowsable = true,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(TravelModeSettingsController(context))
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "Avoid Traffic",
            isToggleChecked = SdkCall.execute { RoutingInstance.avoidTraffic } ?: false,
            hasToggle = true,
            onToggleChanged = {
                SdkCall.execute {
                    RoutingInstance.avoidTraffic = it
                }
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "Avoid Motorways",
            isToggleChecked = SdkCall.execute { RoutingInstance.avoidMotorways } ?: false,
            hasToggle = true,
            onToggleChanged = {
                SdkCall.execute {
                    RoutingInstance.avoidMotorways = it
                }
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "Avoid Toll Roads",
            isToggleChecked = SdkCall.execute { RoutingInstance.avoidTollRoads } ?: false,
            hasToggle = true,
            onToggleChanged = {
                SdkCall.execute {
                    RoutingInstance.avoidTollRoads = it
                }
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "Avoid Ferries",
            isToggleChecked = SdkCall.execute { RoutingInstance.avoidFerries } ?: false,
            hasToggle = true,
            onToggleChanged = {
                SdkCall.execute {
                    RoutingInstance.avoidFerries = it
                }
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "Avoid Unpaved Roads",
            isToggleChecked = SdkCall.execute { RoutingInstance.avoidUnpavedRoads }
                ?: false,
            hasToggle = true,
            onToggleChanged = {
                SdkCall.execute {
                    RoutingInstance.avoidUnpavedRoads = it
                }
            }
        ))
    }

}
