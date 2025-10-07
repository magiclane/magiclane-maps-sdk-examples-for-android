/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.services.RoutingInstance
import com.magiclane.sdk.util.EnumHelp
import com.magiclane.sdk.util.SdkCall

class TravelModeSettingsController(context: CarContext) : GeneralSettingsScreen(context) {

    override fun updateData() {
        title = "Travel Mode"
        headerAction = UIActionModel.backModel()
        isSelectableList = true

        selectedItemIndex = SdkCall.execute { RoutingInstance.travelMode.value } ?: 0

        listItemModelList = ArrayList()
        listItemModelList.add(GenericListItemModel(title = "Fastest"))
        listItemModelList.add(GenericListItemModel(title = "Shortest"))
        listItemModelList.add(GenericListItemModel(title = "Economic"))
    }

    override fun didSelectItem(index: Int) {
        SdkCall.execute { RoutingInstance.travelMode = EnumHelp.fromInt(index) }
    }

}