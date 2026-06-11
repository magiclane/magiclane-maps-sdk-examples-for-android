/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.GenericCategories
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.Service
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.QuickLeftListScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AppProcess
import com.magiclane.sdk.examples.androidautoroutenavigation.services.SearchInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.util.INVALID_ID
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.util.EnumHelp
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall

typealias PoisScreen = QuickLeftListScreen
typealias SubPoisScreen = QuickLeftListScreen

class PoiCategoriesController(context: CarContext) : PoisScreen(context) {

    override fun updateData() {
        title = context.getString(R.string.points_of_interest)
        headerAction = UIActionModel.backModel()

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

        listItemModelList = getItems()
    }

    private fun getItems(): ArrayList<GenericListItemModel> {
        val result = ArrayList<GenericListItemModel>()

        SdkCall.execute {
            val categories = GenericCategories().categories ?: arrayListOf()

            categories.forEach {
                val model = GenericListItemModel()

                model.title = it.name ?: return@forEach
                model.icon = it.image?.asBitmap(100, 100)
                model.isBrowsable = true
                model.onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    val categoryId = SdkCall.execute { it.id } ?: INVALID_ID

                    if (categoryId == INVALID_ID) {
                        return@onClicked
                    }

                    Service.pushScreen(PoiSubCategoriesController(context, categoryId))
                }

                result.add(model)
            }
        }

        return result
    }
}

class PoiSubCategoriesController(context: CarContext, private val categoryId: Int) :
    SubPoisScreen(context) {
    private var reference: Coordinates? = null
    val results: LandmarkList = arrayListOf()

    override fun onCreate() {
        super.onCreate()

        SdkCall.execute {
            SearchInstance.service.cancelSearch()

            SearchInstance.service.onStarted = {
                isLoading = true

                invalidate()
                updateMapView()
            }

            SearchInstance.service.onCompleted = { results, _, _ ->
                isLoading = false
                this.results.addAll(results)

                invalidate()
            }

            SearchInstance.service.preferences.searchAddressesEnabled = false
        }

        isLoading = true
        search()
    }

    override fun onDestroy() {
        super.onDestroy()

        SdkCall.execute {
            SearchInstance.service.onStarted = null
            SearchInstance.service.onCompleted = null
            SearchInstance.service.cancelSearch()
        }
    }

    override fun updateData() {
        title = SdkCall.execute { GenericCategories().getCategory(categoryId)?.name } ?: "..."
        headerAction = UIActionModel.backModel()

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

        listItemModelList = getItems()
    }

    private fun search() = SdkCall.execute {
        val category: EGenericCategoriesIDs = EnumHelp.fromInt(categoryId)
        reference = AppProcess.currentPosition ?: return@execute

        SearchInstance.service.cancelSearch()
        SearchInstance.service.searchAroundPosition(category, reference)
    }

    private fun getItems(): ArrayList<GenericListItemModel> {
        val result = ArrayList<GenericListItemModel>()

        SdkCall.execute {
            results.forEach { searchedLandmark ->
                val model = asModel(searchedLandmark, reference) ?: return@forEach
                model.onClicked = onClicked@{
                    if (Service.topScreen != this) {
                        return@onClicked
                    }

                    Service.pushScreen(RoutesPreviewController(context, searchedLandmark), true)
                }
                result.add(model)
            }
        }

        return result
    }

    private fun asModel(landmark: Landmark?, reference: Coordinates?): GenericListItemModel? {
        reference ?: return null
        landmark ?: return null

        val coordinates = landmark.coordinates ?: return null
        val nameDesc = GemUtil.pairFormatLandmarkDetails(landmark)

        val item = GenericListItemModel()
        item.distanceInMeters = coordinates.getDistance(reference).toInt()
        item.lat = coordinates.latitude
        item.lon = coordinates.longitude
        item.title = nameDesc.first
        item.description = nameDesc.second
        item.icon = landmark.image?.asBitmap(100, 100)

        if (item.title.isEmpty()) {
            return null
        }
        return item
    }
}
