/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.DurationSpan
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIRouteModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.util.Util
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

        val builder = MapWithContentTemplate.Builder()
        val listTemplate = ListTemplate.Builder()
        if (isLoading) {
            listTemplate.setLoading(true)
        } else {
            listTemplate.setSingleList(getItemList())
        }

        listTemplate.setHeader(
            Header.Builder()
                .setTitle(title)
                .setStartHeaderAction(Action.BACK)
                .build(),
        )

        builder.setContentTemplate(listTemplate.build())

        Util.getActionStrip(context, actionStripModelList)?.let { builder.setActionStrip(it) }

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

        var selectedIndex = selectedIndex
        if (selectedIndex == -1) {
            selectedIndex = 0
        }

        for (i in 0 until count) {
            createRow(items[i], i == selectedIndex)?.let { builder.addItem(it) }
        }

        builder.setSelectedIndex(selectedIndex)

        builder.setOnSelectedListener { index ->
            didSelectItem(index)
        }

//        builder.setOnItemsVisibilityChangedListener { start, end ->
//            onItemsVisibilityChangedListener?.let { it(start, end) }
//        }

        return builder.build()
    }

    private fun createRow(item: UIRouteModel, selectedItem: Boolean): Row? {
        val distance: Distance = if (item.totalDistance >= 1000) {
            Distance.create(
                (item.totalDistance / 1000.0f).toDouble(),
                Distance.UNIT_KILOMETERS,
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
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE,
                )
            }

            val builder = Row.Builder()

            builder.setTitle(item.title)
            builder.addText(description)

            if (selectedItem) {
                val startText = SpannableStringBuilder.valueOf(context.getString(R.string.start)).apply {
                    setSpan(
                        ForegroundCarColorSpan.create(CarColor.BLUE),
                        0,

                        this.length,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE,
                    )
                }
                builder.addText(startText)
            }

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }
}
