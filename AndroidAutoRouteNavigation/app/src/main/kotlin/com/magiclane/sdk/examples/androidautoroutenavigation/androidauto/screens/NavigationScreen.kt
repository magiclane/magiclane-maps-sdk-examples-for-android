/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens

import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.CarNavigationData
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.util.Util

abstract class NavigationScreen(context: CarContext) : GemScreen(context) {
    var backgroundColor = CarColor.GREEN

    var navigationData = CarNavigationData()
    var actionStripModelList: ArrayList<UIActionModel> = arrayListOf()
    var mapActionStripModelList: ArrayList<UIActionModel> = arrayListOf()

    init {
        isMapVisible = true
    }

    override fun onGetTemplate(): Template {
        updateData()

        val builder = NavigationTemplate.Builder()
        builder.setBackgroundColor(backgroundColor)

        navigationData.getRoutingInfo(context)?.let { builder.setNavigationInfo(it) }
        navigationData.getTravelEstimate()?.let { builder.setDestinationTravelEstimate(it) }

        Util.getActionStrip(context, actionStripModelList)?.let { builder.setActionStrip(it) }
        Util.getActionStrip(context, mapActionStripModelList)?.let { builder.setMapActionStrip(it) }

        return builder.build()
    }
}
