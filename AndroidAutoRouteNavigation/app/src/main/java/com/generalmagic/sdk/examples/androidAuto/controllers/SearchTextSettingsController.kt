/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.androidAuto.controllers

import androidx.car.app.CarContext
import com.generalmagic.sdk.examples.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.screens.ListScreen
import com.generalmagic.sdk.examples.services.SearchInstance
import com.generalmagic.sdk.util.SdkCall

typealias SearchSettingsScreen = ListScreen

class SearchTextSettingsController(context: CarContext) : SearchSettingsScreen(context) {

    override fun updateData() {
        title = "Search Settings"
        headerAction = UIActionModel.backModel()

        listItemModelList = ArrayList()
        fillSettings()
    }

    private fun fillSettings() = SdkCall.execute {
        listItemModelList.add(GenericListItemModel(
            title = "Allow map POIs",
            isToggleChecked = SearchInstance.searchMapPOIsEnabled,
            hasToggle = true,
            onToggleChanged = {
                SdkCall.execute { SearchInstance.searchMapPOIsEnabled = it }
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "Search through addresses",
            isToggleChecked = SearchInstance.searchAddressesEnabled,
            hasToggle = true,
            onToggleChanged = {
                SdkCall.execute { SearchInstance.searchAddressesEnabled = it }
            }
        ))
    }

}