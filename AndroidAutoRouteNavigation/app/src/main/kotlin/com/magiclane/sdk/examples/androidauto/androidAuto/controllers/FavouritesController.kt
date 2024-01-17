/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
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
