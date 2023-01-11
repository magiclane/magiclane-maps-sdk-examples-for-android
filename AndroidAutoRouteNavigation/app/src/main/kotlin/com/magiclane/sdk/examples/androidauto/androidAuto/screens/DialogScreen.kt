/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
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
import androidx.car.app.model.CarIcon
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel

abstract class DialogScreen(context: CarContext) : GemScreen(context) {
    var isLongTemplate: Boolean = false
    var title: String = ""
    var message: String = ""
    var icon: CarIcon? = null
    var actionStripModelList: ArrayList<UIActionModel> = arrayListOf()
    var actionsList: ArrayList<UIActionModel> = arrayListOf()
    var headerAction: UIActionModel = UIActionModel()

    override fun onGetTemplate(): Template {
        updateData()

        val headerAction = UIActionModel.createAction(context, headerAction)

        val actionStrip = Util.getActionStrip(context, actionStripModelList)

        if (isLongTemplate) {
            val builder = LongMessageTemplate.Builder(message)

            builder.setTitle(title)
            builder.setHeaderAction(headerAction)

            actionStrip?.let { builder.setActionStrip(it) }
            actionsList.forEach { model ->
                builder.addAction(UIActionModel.createAction(context, model))
            }

            return builder.build()
        } else {
            val builder = MessageTemplate.Builder(message)

            builder.setTitle(title)
            builder.setHeaderAction(headerAction)

            if (!isLoading)
                icon?.let { builder.setIcon(it) }
            builder.setLoading(isLoading)

            actionStrip?.let { builder.setActionStrip(it) }
            actionsList.forEach { model ->
                builder.addAction(UIActionModel.createAction(context, model))
            }

            return builder.build()
        }
    }
}