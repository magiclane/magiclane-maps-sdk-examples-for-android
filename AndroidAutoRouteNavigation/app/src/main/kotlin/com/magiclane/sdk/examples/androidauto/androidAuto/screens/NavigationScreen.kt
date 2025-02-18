/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.androidAuto.screens

import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import com.magiclane.sdk.examples.androidauto.androidAuto.model.CarNavigationData
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util

abstract class NavigationScreen(context: CarContext) : GemScreen(context) {
    var backgroundColor = CarColor.GREEN

    var navigationData = CarNavigationData()
    var actionStripModelList: ArrayList<UIActionModel> = arrayListOf()
    var mapActionStripModelList: ArrayList<UIActionModel> = arrayListOf()

    init {
        isMapVisible = true
    }

    // -------------------------------

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
