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
import com.generalmagic.sdk.core.EGenericCategoriesIDs
import com.generalmagic.sdk.core.GenericCategories
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.examples.androidAuto.Service
import com.generalmagic.sdk.examples.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.screens.QuickLeftListScreen
import com.generalmagic.sdk.examples.app.AppProcess
import com.generalmagic.sdk.examples.services.SearchInstance
import com.generalmagic.sdk.examples.util.INVALID_ID
import com.generalmagic.sdk.examples.util.TextsUtil
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.util.EnumHelp
import com.generalmagic.sdk.util.SdkCall

typealias PoisScreen = QuickLeftListScreen
typealias SubPoisScreen = QuickLeftListScreen

class PoiCategoriesController(context: CarContext) : PoisScreen(context) {

    override fun updateData() {
        title = "Points of interest"
        headerAction = UIActionModel.backModel()

        actionStripModelList = ArrayList()

        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_search_white_24dp,
            onClicked = {
                Service.show(SearchTextController(context))
            }
        ))

        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_settings_white_24,
            onClicked = {
                Service.show(GeneralSettingsController(context))
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
                    val categoryId = SdkCall.execute { it.id } ?: INVALID_ID

                    if (categoryId == INVALID_ID)
                        return@onClicked

                    Service.show(PoiSubCategoriesController(context, categoryId))
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
            onClicked = {
                Service.show(SearchTextController(context))
            }
        ))

        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_settings_white_24,
            onClicked = {
                Service.show(GeneralSettingsController(context))
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
                model.onClicked = {
                    Service.show(RoutesPreviewController(context, searchedLandmark))
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
