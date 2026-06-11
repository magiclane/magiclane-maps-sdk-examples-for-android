/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.Service
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.SearchScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AppProcess
import com.magiclane.sdk.examples.androidautoroutenavigation.services.SearchInstance
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall

class SearchTextController(context: CarContext) : SearchScreen(context) {
    private var reference: Coordinates? = null
    val results: LandmarkList = arrayListOf()

    override fun onCreate() {
        super.onCreate()

        SdkCall.execute {
            SearchInstance.service.cancelSearch()

            SearchInstance.service.preferences.removeAllCategoryFilters()

            noDataText = " "

            SearchInstance.service.onStarted = {
                if (!isLoading) {
                    isLoading = true
                    noDataText = " "
                    invalidate()
                }
            }

            SearchInstance.service.onCompleted = onCompleted@{ results, error, _ ->
                if (error != GemError.Cancel) {
                    isLoading = false

                    if (results.isEmpty()) {
                        noDataText = context.getString(R.string.no_results_found)
                    }

                    this.results.clear()
                    this.results.addAll(results)

                    invalidate()
                }
            }

            SearchInstance.service.preferences.searchAddressesEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        SdkCall.execute {
            SearchInstance.service.onStarted = null
            SearchInstance.service.onCompleted = null
            SearchInstance.service.cancelSearch()
        }
    }

    override fun onTextInputChanged(value: String) {
        doSearch(value)
    }

    override fun onTextInputSubmit(value: String) {
        doSearch(value)
    }

    override fun updateData() {
        showKeyboardByDefault = true
        headerAction = UIActionModel.backModel()
        listItemModelList = getItems(results, reference)
    }

    private fun doSearch(text: String) = SdkCall.execute {
        reference = AppProcess.currentPosition

        SearchInstance.service.cancelSearch()

        if (text.isNotEmpty()) {
            SearchInstance.service.searchByFilter(text, reference)
        } else {
            results.clear()
            invalidate()
        }
    }

    private fun getItems(searchResultList: LandmarkList?, reference: Coordinates?): ArrayList<GenericListItemModel> {
        if (searchResultList == null) return arrayListOf()

        return SdkCall.execute {
            val result = ArrayList<GenericListItemModel>()
            for (searchResult in searchResultList) {
                val model = asSearchModel(searchResult, reference) ?: continue
                model.onClicked = onClicked@{
                    if (Service.topScreen != this) return@onClicked
                    Service.pushScreen(RoutesPreviewController(context, searchResult), true)
                }
                result.add(model)
            }
            result
        } ?: arrayListOf()
    }

    companion object {
        fun asSearchModel(item: Landmark, reference: Coordinates?): GenericListItemModel? {
            val coordinates = item.coordinates ?: return null
            val nameDesc = GemUtil.pairFormatLandmarkDetails(item)

            val distanceInMeters = reference?.let { coordinates.getDistance(it).toInt() } ?: -1

            val distText = if (distanceInMeters != -1) {
                GemUtil.getDistText(distanceInMeters, EUnitSystem.Metric, true)
            } else {
                null
            }

            val customDescription = Pair(nameDesc.first, nameDesc.second)

            val title = customDescription.first

            val description = if (distText != null) {
                "${distText.first}${distText.second} · ${customDescription.second}"
            } else {
                customDescription.second
            }

            val result = GenericListItemModel()
            result.lat = coordinates.latitude
            result.lon = coordinates.longitude
            result.title = title
            result.description = description
            result.icon = item.imageAsBitmap(100)

            return result
        }
    }
}
