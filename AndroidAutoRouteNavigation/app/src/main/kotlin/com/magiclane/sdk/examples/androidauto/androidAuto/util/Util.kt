/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
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

@Suppress("unused")
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

        val config = sourceBitmap.config ?: Bitmap.Config.ARGB_8888
        val resultBitmap = sourceBitmap.copy(config, true)
        val paint = Paint()
        paint.colorFilter = LightingColorFilter(color, 1)

        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(resultBitmap, 0.0f, 0.0f, paint)
        return resultBitmap
    }
}
