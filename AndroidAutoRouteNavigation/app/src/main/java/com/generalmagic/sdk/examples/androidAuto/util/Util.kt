/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.androidAuto.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.car.app.CarContext
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel

object Util {
    fun asCarIcon(context: CarContext, value: Bitmap?): CarIcon? {
        value ?: return null
        try {
            val icon = Icon.createWithBitmap(value)
            val iconCompat = IconCompat.createFromIcon(context, icon) ?: return null
            return CarIcon.Builder(iconCompat).build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun getDrawableIcon(
        context: Context,
        resId: Int,
        color: CarColor? = null
    ): CarIcon {
        val builder = CarIcon.Builder(IconCompat.createWithResource(context, resId))
        color?.let { builder.setTint(it) }

        return builder.build()
    }

    fun getActionStrip(context: CarContext, actions: ArrayList<UIActionModel>): ActionStrip? {
        val builder = ActionStrip.Builder()

        if (actions.isEmpty())
            return null

        actions.forEach { model ->
            builder.addAction(UIActionModel.createAction(context, model))
        }

        return builder.build()
    }
}
