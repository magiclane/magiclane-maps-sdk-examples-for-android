/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.androidAuto.util

import android.graphics.Bitmap
import androidx.car.app.CarContext
import com.generalmagic.sdk.core.ImageDatabase
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.examples.util.Util.changeBitmapColor
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages

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