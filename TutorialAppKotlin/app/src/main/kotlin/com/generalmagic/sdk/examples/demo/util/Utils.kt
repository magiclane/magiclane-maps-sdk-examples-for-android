/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.demo.util

import android.app.Activity
import android.graphics.Color
import android.graphics.Insets
import android.os.Build
import android.util.TypedValue
import android.view.WindowInsets
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.places.EAddressField
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkUtil.getUIString
import com.generalmagic.sdk.util.StringIds
import java.util.*


// //// copied from android-kt
object Utils {
    fun getImageAspectRatio(nImageId: Int): Float {
        return SdkCall.execute {
            var fAspectRatio = 1.0f
            val icon = ImageDatabase().getImageById(nImageId)
            if (icon != null) {
                val size = icon.size

                if (size != null && size.height != 0) {
                    fAspectRatio = (size.width.toFloat() / size.height)
                }
            }

            return@execute fAspectRatio
        } ?: 0.0f
    }

    fun getFormattedWaypointName(
        landmark: Landmark,
        bFillAddressInfo: Boolean = false
    ): String {
        return SdkCall.execute {
            if (bFillAddressInfo) {
                UtilUITexts.fillLandmarkAddressInfo(landmark)
            }

            var text = "" // result

            val landmarkAddress = landmark.addressInfo ?: return@execute ""

            var name = landmark.name ?: ""
            val streetName = landmarkAddress.getField(EAddressField.StreetName) ?: ""
            val streetNumber = landmarkAddress.getField(EAddressField.StreetNumber) ?: ""
            val settlement = landmarkAddress.getField(EAddressField.Settlement) ?: ""
            val city = landmarkAddress.getField(EAddressField.City) ?: ""
            val county = landmarkAddress.getField(EAddressField.County) ?: ""
            val state = landmarkAddress.getField(EAddressField.State) ?: ""
            val nearCity = ""
            val nearSettlement = ""

            if (city.isNotEmpty()) {
                nearCity.format(getUIString(StringIds.eStringNodeNearby), city)
            }

            if (settlement.isNotEmpty()) {
                nearSettlement.format(getUIString(StringIds.eStringNodeNearby), settlement)
            }

            if (streetName.isNotEmpty() && name.isNotEmpty()) {
                if ((name == nearCity) || (name == nearSettlement)) {
                    name = ""
                }
            }

            if (name.isNotEmpty()) {
                val pos = name.indexOf("; ")
                if (pos > 0) {
                    val description = name.substring(pos + 2)
                    if (description.isNotEmpty()) {
                        if (((settlement.isNotEmpty()) && (description.indexOf(settlement) >= 0)) ||
                            ((city.isNotEmpty()) && (description.indexOf(city) >= 0)) ||
                            ((county.isNotEmpty()) && (description.indexOf(county) >= 0)) ||
                            ((state.isNotEmpty()) && (description.indexOf(state) >= 0))
                        ) {
                            // waypoint name was already formatted
                            text = name
                            text.replace(";", ",")
                            return@execute text
                        }
                    }
                }
            }

            val unknownXYZ = "Unknown_xyz"
            val bNameEmpty = name.isEmpty()
            var bRestoreStreetName = false

            landmark.name = ""
            if ((streetName.isEmpty()) && !bNameEmpty) {
                landmarkAddress.setField(unknownXYZ, EAddressField.StreetName)
                bRestoreStreetName = true
            }

            val nameDescription = UtilUITexts.pairFormatLandmarkDetails(landmark, true)
            var landmarkName = nameDescription.first
            val landmarkDescription = nameDescription.second

            landmarkName.trim()
            landmarkDescription.trim()

            if (landmarkName == unknownXYZ) {
                landmarkName = ""
            }

            landmark.name = name
            if (bRestoreStreetName) {
                landmarkAddress.setField(streetName, EAddressField.StreetName)
            }

            var nameParts = listOf<String>()
            if (!bNameEmpty) {
                nameParts = name.split(";,")

                for (str in nameParts) {
                    str.trim()
                }
            }

            val bContainsStreetNumber = (name.indexOf("<") == 0) && (name.indexOf("<<") < 0) &&
                (name.indexOf(">") > 0) && (name.indexOf(">>") < 0) &&
                (name.indexOf(streetNumber) == 1)

            val nameContainsText: (text: String) -> Boolean = {
                var result = false
                for (str in nameParts) {
                    if (str == text) {
                        result = true
                        break
                    }
                }

                result
            }

            val bContainsStreetName = nameContainsText(streetName) ||
                (
                    bContainsStreetNumber && (nameParts.isNotEmpty()) && (
                        nameParts[0].indexOf(
                            streetName
                        ) > 0
                        )
                    )

            val bContainsCityOrSettlementName = nameContainsText(city) ||
                nameContainsText(settlement) ||
                nameContainsText(nearCity) ||
                nameContainsText(nearSettlement)

            val bContainsLandmarkDescription = nameContainsText(landmarkDescription)

            if (bNameEmpty || bContainsStreetName) {
                text = landmarkName
                if ((text.isNotEmpty()) && (landmarkDescription.isNotEmpty())) {
                    text += ", $landmarkDescription"
                } else if (text.isEmpty()) {
                    if (bNameEmpty) {
                        text = getUIString(StringIds.eStringUnnamedStreet)
                        if (landmarkDescription.isNotEmpty()) {
                            text += ", $landmarkDescription"
                        }
                    } else {
                        text = name
                        if (!bContainsLandmarkDescription &&
                            (landmarkDescription.isNotEmpty()) &&
                            !bContainsCityOrSettlementName
                        ) {
                            text += ", $landmarkDescription"
                        }
                    }
                }
            } else if (!bNameEmpty) {
                text = name
                if (!bContainsLandmarkDescription &&
                    (landmarkDescription.isNotEmpty()) &&
                    !bContainsCityOrSettlementName
                ) {
                    text += ", $landmarkDescription"
                }
            }

            return@execute text
        } ?: ""
    }

    fun getScreenWidth(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            val displayMetrics = GEMApplication.applicationContext().resources.displayMetrics
            displayMetrics.widthPixels
        }
    }

    fun getScreenHeight(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.height() - insets.top - insets.bottom
        } else {
            val displayMetrics = GEMApplication.applicationContext().resources.displayMetrics
            displayMetrics.heightPixels
        }
    }
}


object AppUtils {
    fun getColor(gemSdkColor: Int): Int {
        val r = 0x000000ff and gemSdkColor
        val g = 0x000000ff and (gemSdkColor shr 8)
        val b = 0x000000ff and (gemSdkColor shr 16)
        val a = 0x000000ff and (gemSdkColor shr 24)

        return Color.argb(a, r, g, b)
    }

    fun getSizeInPixels(dpi: Int): Int {
        val metrics = GEMApplication.applicationContext().resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpi.toFloat(), metrics)
            .toInt()
    }

    fun getSizeInPixelsFromMM(mm: Int): Int {
        val metrics = GEMApplication.applicationContext().resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm.toFloat(), metrics)
            .toInt()
    }
}

