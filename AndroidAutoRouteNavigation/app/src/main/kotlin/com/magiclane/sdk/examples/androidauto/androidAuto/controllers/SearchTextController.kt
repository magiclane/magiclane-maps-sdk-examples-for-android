/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.androidauto.R
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.SearchScreen
import com.magiclane.sdk.examples.androidauto.app.AppProcess
import com.magiclane.sdk.examples.androidauto.services.SearchInstance
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.GemUtil

class SearchTextController(context: CarContext) : SearchScreen(context) {
    private var reference: Coordinates? = null

    private val listener = ProgressListener.create(
        onStarted = {
            isLoading = true
            invalidate()
        },
        onCompleted = { _, _ ->
            isLoading = false
            invalidate()
        }
    )

    override fun onCreate() {
        super.onCreate()
        SearchInstance.listeners.add(listener)
    }

    override fun onDestroy() {
        super.onDestroy()
        SearchInstance.listeners.remove(listener)
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

        actionStripModelList = arrayListOf()
        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_settings_white_24,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(SearchTextSettingsController(context))
            }
        ))

        listItemModelList = getItems(SearchInstance.results, reference)
    }

    private fun doSearch(text: String) = SdkCall.execute {
        reference = AppProcess.currentPosition

        SearchInstance.service.cancelSearch()

        SearchInstance.service.searchByFilter(text, reference)
    }

    private fun getItems(
        searchResultList: LandmarkList?,
        reference: Coordinates?
    ): ArrayList<GenericListItemModel> {
        if (searchResultList == null) return arrayListOf()

        return SdkCall.execute {
            val result = ArrayList<GenericListItemModel>()
            for (searchResult in searchResultList) {
                val model = asSearchModel(searchResult, reference) ?: continue
                model.onClicked = onClicked@{
                    if (Service.topScreen != this)
                        return@onClicked

                    Service.pushScreen(RoutesPreviewController(context, searchResult), true)
                }

                result.add(model)
            }

            return@execute result
        }!!
    }

    companion object {
        fun asSearchModel(item: Landmark, reference: Coordinates?): GenericListItemModel? {
            val coordinates = item.coordinates ?: return null
            val nameDesc = GemUtil.pairFormatLandmarkDetails(item)

            val distanceInMeters = reference?.let { coordinates.getDistance(it).toInt() } ?: -1

            val distText = if (distanceInMeters != -1)
                GemUtil.getDistText(distanceInMeters, EUnitSystem.Metric, true)
            else null

            val customDescription = Pair(nameDesc.first, nameDesc.second)

            val title = customDescription.first

            val description = if (distText != null) {
                "${distText.first}${distText.second} · ${customDescription.second}"
            } else customDescription.second

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
