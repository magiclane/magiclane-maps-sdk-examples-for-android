/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
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
