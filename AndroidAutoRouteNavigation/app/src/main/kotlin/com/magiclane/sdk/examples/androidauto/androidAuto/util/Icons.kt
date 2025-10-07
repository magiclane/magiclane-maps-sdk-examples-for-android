/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.androidAuto.util

import android.graphics.Bitmap
import androidx.car.app.CarContext
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.examples.androidauto.util.Util.changeBitmapColor
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages

object Icons {
    fun getPinEndIcon(): Bitmap? = SdkCall.execute {
        val image =
            ImageDatabase().getImageById(SdkImages.Engine_Misc.WaypointFlag_PointFinish_SearchOnMap.value)
        return@execute image?.asBitmap(100, 100)
    }

    fun getReportIcon(context: CarContext): Bitmap? = SdkCall.execute {
        val image = ImageDatabase().getImageById(SdkImages.SocialReports.SR_Reports.value)
        val bitmap = image?.asBitmap(100, 100)

        val color = if (!context.isDarkMode) {
            Rgba.white()
        } else {
            Rgba.black()
        }

        return@execute changeBitmapColor(bitmap, color.argbValue)
    }
}