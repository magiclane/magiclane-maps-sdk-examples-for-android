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
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidauto.services.RoutingInstance
import com.generalmagic.sdk.util.EnumHelp
import com.generalmagic.sdk.util.SdkCall

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