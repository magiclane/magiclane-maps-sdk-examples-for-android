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

import android.Manifest
import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidauto.R
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.QuickLeftListScreen
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Icons
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall

// used for : (Service.screenManager.top as? MainScreen).
abstract class MainScreen(context: CarContext) : QuickLeftListScreen(context)
//typealias MainScreen = QuickLeftListScreen

class MainMenuController(context: CarContext) : MainScreen(context) {

    override fun updateData() {
        title = "Android Auto Example"
        headerAction = UIActionModel.appIconModel()

        listItemModelList = ArrayList()
        listItemModelList.add(GenericListItemModel(
            title = "Map select",
            icon = Icons.getPinEndIcon(),
            isBrowsable = true,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(PickDestinationController(context))
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "History",
            iconId = R.drawable.ic_baseline_history_24,
            isBrowsable = true,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(HistoryController(context))
            }
        ))

        listItemModelList.add(GenericListItemModel(
            title = "Points of interest",
            iconId = R.drawable.ic_baseline_interests_24,
            isBrowsable = true,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(PoiCategoriesController(context))
            }
        ))

//        listItemModelList.add(GenericListItemModel(
//            title = "Favourites",
//            iconId = R.drawable.ic_baseline_star_24,
//            isBrowsable = true,
//            onClicked = {
//                Service.pushScreen(FavouritesController(context))
//            }
//        ))

        actionStripModelList = ArrayList()
        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_search_white_24dp,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(SearchTextController(context))
            }
        ))

        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_settings_white_24,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(GeneralSettingsController(context))
            }
        ))

        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        val have = PermissionsHelper.hasPermissions(context, permissions.toTypedArray())
        if (!have) {
            context.requestPermissions(ArrayList<String>(permissions)) { granted, rejected ->
                if (rejected.isNotEmpty())
                    return@requestPermissions

                SdkCall.execute {
                    PermissionsHelper.onRequestPermissionsResult(granted, rejected)
                }

                Service.invalidateTop()
            }
        }
    }
}
