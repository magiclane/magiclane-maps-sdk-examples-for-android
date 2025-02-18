/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.ListScreen
import com.magiclane.sdk.examples.androidauto.services.RoutingInstance
import com.magiclane.sdk.util.SdkCall

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
            isBrowsable = true,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(AvoidTrafficSettingsController(context))
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
