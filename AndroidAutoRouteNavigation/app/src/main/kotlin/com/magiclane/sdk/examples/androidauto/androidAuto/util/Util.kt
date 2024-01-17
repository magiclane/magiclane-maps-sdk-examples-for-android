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

package com.magiclane.sdk.examples.androidauto.androidAuto.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.car.app.CarContext
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel

object Util {
    fun asCarIcon(context: CarContext, value: Bitmap?, color: CarColor? = null): CarIcon? {
        value ?: return null
        try {
            val icon = Icon.createWithBitmap(value)
            val iconCompat = IconCompat.createFromIcon(context, icon) ?: return null

            val builder = CarIcon.Builder(iconCompat)
            color?.let { builder.setTint(it) }
            return builder.build()
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
            val action = UIActionModel.createAction(context, model)
            action?.let { builder.addAction(it) }
        }

        return builder.build()
    }

    fun changeBitmapColor(sourceBitmap: Bitmap?, color: Int): Bitmap? {
        sourceBitmap ?: return null

        val resultBitmap = sourceBitmap.copy(sourceBitmap.config, true)
        val paint = Paint()
        paint.colorFilter = LightingColorFilter(color, 1)

        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(resultBitmap, 0.0f, 0.0f, paint)
        return resultBitmap
    }
}
