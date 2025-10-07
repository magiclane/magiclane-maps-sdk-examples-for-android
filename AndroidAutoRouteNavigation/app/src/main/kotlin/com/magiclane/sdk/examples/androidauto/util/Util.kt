/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.magiclane.sdk.examples.androidauto.util

import android.app.UiModeManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import com.magiclane.sdk.places.Coordinates

const val INVALID_ID = -1
const val INVALID_INDEX = -1

typealias OnClick = (() -> Unit)
typealias OnToggleChanged = ((Boolean) -> Unit)


typealias Parameter = Pair<String, String>

inline fun <reified T> ArrayList(size: Int, factory: ((Int) -> T)): ArrayList<T> {
    val result = ArrayList<T>()
    for (i in 0 until size)
        result.add(factory(i))
    return result
}

object Util {
    fun isNightModeActive(context: Context): Boolean {
        val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiManager?.currentModeType == UiModeManager.MODE_NIGHT_YES
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

    fun mmToPixels(mm: Int, dpi: Int): Int = (((dpi * mm) / 25.4) + 0.5).toInt()

    fun isGeoIntent(uri: String): Boolean = uri.contains("geo:")

    fun parseCoordinates(text: String): Coordinates? {
        val coordinateList = text.split(",")
        if ((coordinateList.size == 2) && isNumber(coordinateList[0]) && isNumber(coordinateList[1])) {
            val result = Coordinates(coordinateList[0].toDouble(), coordinateList[1].toDouble())

            if (result.valid()) {
                return result
            }
            return null
        }

        return null
    }

    fun isNumber(text: String): Boolean {
        text.replace(" ", "")
        text.replace(",", "")

        val regex = "^-?\\d*\\.?\\d+".toRegex()

        return regex.matches(text)
    }

    fun getParameters(text: String): ArrayList<Parameter> {
        val result = ArrayList<Parameter>()
        text.replace("+", " ")

        val words = text.split("&")

        for (word in words) {
            val pos = word.indexOf('=')
            if ((pos > 0) && pos < (word.length - 1)) {
                result.add(Pair(word.substring(0, pos), word.substring(pos + 1)))
            }
        }

        return result
    }
}
