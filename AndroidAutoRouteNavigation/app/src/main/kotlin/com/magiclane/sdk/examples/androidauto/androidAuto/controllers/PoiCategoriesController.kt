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
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.GenericCategories
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.androidauto.R
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.QuickLeftListScreen
import com.magiclane.sdk.examples.androidauto.app.AppProcess
import com.magiclane.sdk.examples.androidauto.services.SearchInstance
import com.magiclane.sdk.examples.androidauto.util.INVALID_ID
import com.magiclane.sdk.examples.androidauto.util.TextsUtil
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.util.EnumHelp
import com.magiclane.sdk.util.SdkCall

typealias PoisScreen = QuickLeftListScreen
typealias SubPoisScreen = QuickLeftListScreen

class PoiCategoriesController(context: CarContext) : PoisScreen(context) {

    override fun updateData() {
        title = "Points of interest"
        headerAction = UIActionModel.backModel()

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
                    if (Service.topScreen != this)
                        return@onClicked

                    val categoryId = SdkCall.execute { it.id } ?: INVALID_ID

                    if (categoryId == INVALID_ID)
                        return@onClicked

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

    private val searchListener = ProgressListener.create(
        onStarted = {
            isLoading = true

            invalidate()
            updateMapView()
        },
        onCompleted = { _, _ ->
            isLoading = false

            invalidate()
            updateMapView()
        }
    )

    override fun onCreate() {
        super.onCreate()
        SearchInstance.listeners.add(searchListener)

        isLoading = true
        search()
    }

    override fun onDestroy() {
        super.onDestroy()
        SearchInstance.listeners.remove(searchListener)
    }

    override fun updateData() {
        title = SdkCall.execute { GenericCategories().getCategory(categoryId)?.name } ?: "..."
        headerAction = UIActionModel.backModel()

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

        listItemModelList = getItems()
    }

    override fun updateMapView() {
        super.updateMapView()

        SdkCall.execute {
            mapView?.activateHighlightLandmarks(SearchInstance.results)
        }
    }

    private fun search() = SdkCall.execute {
        val category: EGenericCategoriesIDs = EnumHelp.fromInt(categoryId)
        reference = AppProcess.currentPosition ?: return@execute

        SearchInstance.service.searchAroundPosition(category, reference)
    }

    private fun getItems(): ArrayList<GenericListItemModel> {
        val result = ArrayList<GenericListItemModel>()

        SdkCall.execute {
            SearchInstance.results.forEach { searchedLandmark ->
                val model = asModel(searchedLandmark, reference) ?: return@forEach
                model.onClicked = onClicked@{
                    if (Service.topScreen != this)
                        return@onClicked

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
        val nameDesc = TextsUtil.pairFormatLandmarkDetails(landmark)

        val item = GenericListItemModel()
        item.distanceInMeters = coordinates.getDistance(reference).toInt()
        item.lat = coordinates.latitude
        item.lon = coordinates.longitude
        item.title = nameDesc.first
        item.description = nameDesc.second
        item.icon = landmark.image?.asBitmap(100, 100)

        if (item.title.isEmpty())
            return null
        return item
    }
}
