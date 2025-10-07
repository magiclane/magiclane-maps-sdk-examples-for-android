/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
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
