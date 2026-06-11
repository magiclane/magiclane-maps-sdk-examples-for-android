/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.services.RoutingInstance
import com.magiclane.sdk.util.EnumHelp
import com.magiclane.sdk.util.SdkCall

class TravelModeSettingsController(context: CarContext) : GeneralSettingsScreen(context) {

    override fun updateData() {
        title = context.getString(R.string.travel_mode)
        headerAction = UIActionModel.backModel()
        isSelectableList = true

        selectedItemIndex = SdkCall.execute { RoutingInstance.travelMode.value } ?: 0

        listItemModelList = ArrayList()
        listItemModelList.add(GenericListItemModel(title = context.getString(R.string.fastest)))
        listItemModelList.add(GenericListItemModel(title = context.getString(R.string.shortest)))
        listItemModelList.add(GenericListItemModel(title = context.getString(R.string.economic)))
    }

    override fun didSelectItem(index: Int) {
        SdkCall.execute { RoutingInstance.travelMode = EnumHelp.fromInt(index) }
    }
}
