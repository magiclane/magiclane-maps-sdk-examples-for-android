/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import android.Manifest
import androidx.car.app.CarContext
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.Service
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.QuickLeftListScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.util.Icons
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall

// used for : (Service.screenManager.top as? MainScreen).
abstract class MainScreen(context: CarContext) : QuickLeftListScreen(context)
// typealias MainScreen = QuickLeftListScreen

class MainMenuController(context: CarContext) : MainScreen(context) {

    override fun updateData() {
        title = context.getString(R.string.main_menu_title)
        headerAction = UIActionModel.appIconModel()

        listItemModelList = ArrayList()
        listItemModelList.add(
            GenericListItemModel(
                title = context.getString(R.string.map_select),
                icon = Icons.getPinEndIcon(),
                isBrowsable = true,
                onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    Service.pushScreen(PickDestinationController(context))
                },
            ),
        )

        listItemModelList.add(
            GenericListItemModel(
                title = context.getString(R.string.history),
                iconId = R.drawable.ic_baseline_history_24,
                isBrowsable = true,
                onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    Service.pushScreen(HistoryController(context))
                },
            ),
        )

        listItemModelList.add(
            GenericListItemModel(
                title = context.getString(R.string.points_of_interest),
                iconId = R.drawable.ic_baseline_interests_24,
                isBrowsable = true,
                onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    Service.pushScreen(PoiCategoriesController(context))
                },
            ),
        )

//        listItemModelList.add(GenericListItemModel(
//            title = "Favourites",
//            iconId = R.drawable.ic_baseline_star_24,
//            isBrowsable = true,
//            onClicked = {
//                Service.pushScreen(FavouritesController(context))
//            }
//        ))

        actionStripModelList = ArrayList()
        actionStripModelList.add(
            UIActionModel(
                iconId = R.drawable.ic_baseline_search_white_24dp,
                onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    Service.pushScreen(SearchTextController(context))
                },
            ),
        )

        actionStripModelList.add(
            UIActionModel(
                iconId = R.drawable.ic_baseline_settings_white_24,
                onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    Service.pushScreen(GeneralSettingsController(context))
                },
            ),
        )

        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        val have = PermissionsHelper.hasPermissions(context, permissions.toTypedArray())
        if (!have) {
            context.requestPermissions(ArrayList<String>(permissions)) { granted, rejected ->
                if (rejected.isNotEmpty()) {
                    return@requestPermissions
                }

                SdkCall.execute {
                    PermissionsHelper.onRequestPermissionsResult(granted, rejected)
                }

                Service.invalidateTop()
            }
        }
    }
}
