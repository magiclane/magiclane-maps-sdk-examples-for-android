/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.controllers.SearchTextController.Companion.asSearchModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.ListScreen
import com.magiclane.sdk.examples.androidauto.app.AppProcess
import com.magiclane.sdk.examples.androidauto.services.FavouritesInstance
import com.magiclane.sdk.util.SdkCall

typealias FavouritesScreen = ListScreen

class FavouritesController(context: CarContext) : FavouritesScreen(context) {

    override fun updateData() {
        title = "Favourites"
        noDataText = "No favourites from phone app."
        headerAction = UIActionModel.backModel()

        listItemModelList = getItems()
    }

    private fun getItems(): ArrayList<GenericListItemModel> = SdkCall.execute {
        val result = ArrayList<GenericListItemModel>()
        val reference = AppProcess.currentPosition

        FavouritesInstance.favourites.forEach {
            val model = asSearchModel(it, reference) ?: return@forEach
            model.onClicked = {
                Service.pushScreen(RoutesPreviewController(context, it), true)
            }
            result.add(model)
        }

        return@execute result
    } ?: arrayListOf()

}
