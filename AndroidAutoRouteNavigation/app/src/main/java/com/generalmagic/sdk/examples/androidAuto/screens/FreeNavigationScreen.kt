/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidAuto.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import com.generalmagic.sdk.examples.androidAuto.util.Util
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel

abstract class FreeNavigationScreen(context: CarContext) : GemScreen(context) {
    val actionStripModelList: ArrayList<UIActionModel> = arrayListOf()
    val mapActionStripModelList: ArrayList<UIActionModel> = arrayListOf()

    override fun onGetTemplate(): Template {
        updateData()

        val builder = NavigationTemplate.Builder()

        Util.getActionStrip(context, actionStripModelList)?.let { builder.setActionStrip(it) }
        Util.getActionStrip(context, mapActionStripModelList)?.let { builder.setMapActionStrip(it) }

        return builder.build()
    }
}
