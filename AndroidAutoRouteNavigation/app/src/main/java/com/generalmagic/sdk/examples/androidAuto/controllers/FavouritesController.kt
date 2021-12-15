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
import com.generalmagic.sdk.examples.services.FavouritesInstance
import com.generalmagic.sdk.util.SdkCall

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
                Service.show(RoutesPreviewController(context, it))
            }
            result.add(model)
        }

        return@execute result
    } ?: arrayListOf()

}
