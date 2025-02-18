/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.ListScreen
import com.magiclane.sdk.examples.androidauto.services.SearchInstance
import com.magiclane.sdk.util.SdkCall

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