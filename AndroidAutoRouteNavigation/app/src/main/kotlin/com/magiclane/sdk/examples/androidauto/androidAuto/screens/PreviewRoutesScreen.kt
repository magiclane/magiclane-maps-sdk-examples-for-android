/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.androidAuto.screens

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.DurationSpan
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIRouteModel
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util
import kotlin.math.roundToInt

abstract class PreviewRoutesScreen(context: CarContext) : GemScreen(context) {
    var title: String = ""
    var selectedIndex: Int = 0
    var headerAction = UIActionModel()
    var actionStripModelList: ArrayList<UIActionModel> = arrayListOf()
    var navigateAction = UIActionModel()
    var noDataText: String = ""
    var itemModelList: ArrayList<UIRouteModel> = arrayListOf()

    init {
        isMapVisible = true
    }

    abstract fun didSelectItem(index: Int)

    override fun onGetTemplate(): Template {
        updateData()

        val builder = RoutePreviewNavigationTemplate.Builder()

        builder.setLoading(isLoading)
        if (!isLoading) {
            builder.setItemList(getItemList())
        }

        builder.setTitle(title)
        builder.setHeaderAction(Action.BACK)

        Util.getActionStrip(context, actionStripModelList)?.let { builder.setActionStrip(it) }

        val navigateAction = UIActionModel.createAction(context, navigateAction)
        navigateAction?.let { builder.setNavigateAction(it) }

        try {
            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun getItemList(): ItemList {
        val builder = ItemList.Builder()

        val items = itemModelList

        val count = items.size
        if (count == 0) {
            builder.setNoItemsMessage(noDataText)
            return builder.build()
        }

        for (i in 0 until count) {
            createRow(items[i])?.let { builder.addItem(it) }
        }

        var selectedIndex = selectedIndex
        if (selectedIndex == -1)
            selectedIndex = 0

        builder.setSelectedIndex(selectedIndex)

        builder.setOnSelectedListener { index ->
            didSelectItem(index)
        }

//        builder.setOnItemsVisibilityChangedListener { start, end ->
//            onItemsVisibilityChangedListener?.let { it(start, end) }
//        }

        return builder.build()
    }

    private fun createRow(item: UIRouteModel): Row? {
        val distance: Distance = if (item.totalDistance >= 1000) {
            Distance.create(
                (item.totalDistance / 1000.0f).toDouble(),
                Distance.UNIT_KILOMETERS
            )
        } else {
            val roundedDist = (item.totalDistance / 50.0f).roundToInt() * 50
            Distance.create(roundedDist.toDouble(), Distance.UNIT_METERS)
        }

        val durationSpan: DurationSpan = DurationSpan.create(item.totalTime)
        val distanceSpan = DistanceSpan.create(distance)

        try {
            val description = SpannableString("· · ·")
            description.setSpan(durationSpan, 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            description.setSpan(distanceSpan, 4, 5, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

            item.descriptionColor?.let {
                description.setSpan(
                    ForegroundCarColorSpan.create(it),
                    0,
                    5,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
            }

            val builder = Row.Builder()

            builder.setTitle(item.title)
            builder.addText(description)

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}
