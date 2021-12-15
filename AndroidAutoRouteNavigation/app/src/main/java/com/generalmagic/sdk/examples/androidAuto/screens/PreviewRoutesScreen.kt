/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.androidAuto.screens

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.DurationSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.model.UIRouteModel
import kotlin.math.roundToInt

abstract class PreviewRoutesScreen(context: CarContext) : GemScreen(context) {
    var title: String = ""
    var selectedIndex: Int = 0
    var headerAction = UIActionModel()
    var navigateAction = UIActionModel()
    var noDataText: String = ""
    var itemModelList: ArrayList<UIRouteModel> = arrayListOf()

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

        val navigateAction = UIActionModel.createAction(context, navigateAction)
        builder.setNavigateAction(navigateAction)

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

        val textPaint = TextPaint()
        textPaint.color = item.descriptionColor

        durationSpan.updateDrawState(textPaint)
        distanceSpan.updateDrawState(textPaint)

        try {
            val description = SpannableString("· · ·")
            description.setSpan(durationSpan, 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            description.setSpan(distanceSpan, 4, 5, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

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
