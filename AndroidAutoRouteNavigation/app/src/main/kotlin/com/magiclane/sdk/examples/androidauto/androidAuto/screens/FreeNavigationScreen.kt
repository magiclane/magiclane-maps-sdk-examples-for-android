/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel

abstract class FreeNavigationScreen(context: CarContext) : GemScreen(context) {
    var horizontalActions: ArrayList<UIActionModel> = arrayListOf()
    var verticalActions: ArrayList<UIActionModel> = arrayListOf()

    init {
        isMapVisible = true
    }

    override fun onGetTemplate(): Template {
        updateData()

        val builder = NavigationTemplate.Builder()

        Util.getActionStrip(context, horizontalActions)?.let { builder.setActionStrip(it) }
        Util.getActionStrip(context, verticalActions)?.let { builder.setMapActionStrip(it) }

        return builder.build()
    }
}
