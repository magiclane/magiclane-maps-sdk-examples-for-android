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

package com.magiclane.sdk.examples.androidauto.androidAuto.model

import android.graphics.Bitmap
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util

typealias OnClickAction = (() -> Unit)

data class UIActionModel(
    var visible: Boolean? = null,
    var standardType: String? = StandardType.NO_ACTION,
    var text: String? = null,
    var icon: Bitmap? = null,
    var iconId: Int? = null,
    var color: CarColor? = null,
    var onClicked: OnClickAction = {}
) {
    object StandardType {
        val NO_ACTION = null
        const val APP_ICON = "0"
        const val BACK = "1"
        const val PAN = "2"
    }

    companion object {
        fun backModel(): UIActionModel = UIActionModel(
            standardType = StandardType.BACK
        )

        fun appIconModel(): UIActionModel = UIActionModel(
            standardType = StandardType.APP_ICON
        )

        fun panModel(): UIActionModel = UIActionModel(
            standardType = StandardType.PAN
        )

        internal fun createAction(
            carIcon: CarIcon?,
            text: String?,
            color: CarColor?,
            onClicked: OnClickAction
        ): Action? {
            if (carIcon == null && text.isNullOrEmpty())
                return null

            val builder = Action.Builder()
            carIcon?.let { builder.setIcon(it) }
            text?.let { builder.setTitle(it) }
            color?.let { builder.setBackgroundColor(it) }
            builder.setOnClickListener(onClicked)
            return builder.build()
        }

        internal fun createAction(context: CarContext, data: UIActionModel): Action? {
            var action = getStandardAction(data.standardType)
            if (action == null) {
                var carIcon = Util.asCarIcon(context, data.icon)

                if (carIcon == null)
                    carIcon = data.iconId?.let { Util.getDrawableIcon(context, it) }

                action = createAction(carIcon, data.text, data.color, data.onClicked)
            }
            return action
        }

        fun getStandardAction(value: String?): Action? {
            value ?: return null
            return when (value) {
                StandardType.APP_ICON -> Action.APP_ICON
                StandardType.BACK -> Action.BACK
                StandardType.PAN -> Action.PAN

                else -> {
                    null
                }
            }
        }
    }
}
