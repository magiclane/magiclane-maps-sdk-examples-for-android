/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Insets
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowInsets
import com.generalmagic.gemsdk.CommonSettings
import com.generalmagic.gemsdk.TUnitSystem
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.extensions.LocalizationManager
import com.generalmagic.gemsdk.extensions.StringIds
import com.generalmagic.gemsdk.models.Image
import com.generalmagic.gemsdk.models.ImageDatabase
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.TAddressField
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.*


// //// copied from android-kt
class Utils {
    companion object {
        fun getUIString(strId: StringIds): String {
            return GEMSdkCall.execute { LocalizationManager().getString(strId) } ?: ""
        }

        fun getImageAspectRatio(nImageId: Int): Float {
            return GEMSdkCall.execute {
                var fAspectRatio = 1.0f
                val icon = ImageDatabase().getImageById(nImageId)
                if (icon != null) {
                    val size = icon.getSize()

                    if (size != null && size.height() != 0) {
                        fAspectRatio = (size.width().toFloat() / size.height())
                    }
                }

                return@execute fAspectRatio
            } ?: 0.0f
        }

        fun getTimeText(
            timeInSeconds: Int,
            bForceHours: Boolean = false,
            bCapitalizeResult: Boolean = false
        ): Pair<String, String> {
            return GEMSdkCall.execute {
                val time: String
                val unit: String
                if (timeInSeconds == 0) {
                    time = "0"
                    unit = getUIString(StringIds.eStrTimeMin)

                    return@execute Pair(time, unit)
                }

                var nMin: Int
                val nHour: Int

                if ((timeInSeconds >= 3600) || bForceHours) {
                    nMin = timeInSeconds / 60
                    nHour = nMin / 60
                    nMin -= (nHour * 60)

                    time = String.format("%d:%02d", nHour, nMin)
                    unit = getUIString(StringIds.eStrHoursAbbrev)
                } else if (timeInSeconds >= 60) {
                    nMin = timeInSeconds / 60
                    time = String.format("%d", nMin)

                    unit = getUIString(StringIds.eStrTimeMin)
                } else {
                    time = String.format("%d", 1)
                    unit = getUIString(StringIds.eStrTimeMin)
                }

                if (bCapitalizeResult) {
                    time.toUpperCase(Locale.getDefault())
                    unit.toUpperCase(Locale.getDefault())
                }

                return@execute Pair(time, unit)
            } ?: Pair("", "")
        }

        fun getDistText(
            mmeters: Int,
            unitsSystem: TUnitSystem,
            bHighResolution: Boolean = false,
            bCapitalizeUnit: Boolean = false
        ): Pair<String, String> {
            var meters = mmeters
            var distance = ""
            var unit: String

            if (unitsSystem == TUnitSystem.EMetric) {
                val upperLimit: Double = if (bHighResolution) 100000.0 else 20000.0

                if (meters >= upperLimit) {
                    // >20 km - 1 km accuracy - KM
                    meters = ((meters + 500) / 1000) * 1000

                    distance = "${meters.toFloat() / 1000}"
                    unit = getUIString(StringIds.eStrKm)
                } else if ((meters >= 1000) && (meters < upperLimit)) {
                    // 1 - 20 km - 0.1 km accuracy - KM
                    meters = ((meters + 50) / 100) * 100

                    distance = String.format("%.1f", meters.toFloat() / 1000)
                    unit = getUIString(StringIds.eStrKm)
                } else if ((meters >= 500) && (meters < 1000)) {
                    // 500 - 1,000 m - 50 m accuracy - M
                    meters = ((meters + 25) / 50) * 50

                    if (meters == 1000) {
                        distance = String.format("%.1f", meters.toFloat() / 1000)
                        unit = getUIString(StringIds.eStrKm)
                    } else {
                        distance = String.format("%d", meters)
                        unit = getUIString(StringIds.eStrMeter)
                    }
                } else if ((meters >= 200) && (meters < 500)) {
                    // 200 - 500 m - 25 m accuracy - M
                    meters = ((meters + 12) / 25) * 25

                    distance = String.format("%d", meters)
                    unit = getUIString(StringIds.eStrMeter)
                } else if ((meters >= 100) && (meters < 200)) {
                    // 100 - 200 m - 10 m accuracy - M
                    meters = ((meters + 5) / 10) * 10

                    distance = String.format("%d", meters)
                    unit = getUIString(StringIds.eStrMeter)
                } else {
                    // 0 - 100 m - 5 m accuracy - M
                    meters = ((meters + 2) / 5) * 5

                    distance = String.format("%d", meters)
                    unit = getUIString(StringIds.eStrMeter)
                }
            } else {
                val upperLimit: Double = if (bHighResolution) 100.0 else 20.0

                var miles = meters.toDouble() * UtilUITexts.METERS_TO_MILES
                var fMilesThreshold = 0.25f
                var yardsOrFeet: Double

                unit = getUIString(StringIds.eStrYd)

                if (unitsSystem == TUnitSystem.EImperialUK) {
                    yardsOrFeet = miles * UtilUITexts.MILES_TO_YARDS
                } else {
                    fMilesThreshold = 0.1f
                    yardsOrFeet = miles * UtilUITexts.MILES_TO_METERS * UtilUITexts.METERS_TO_FT
                    unit = getUIString(StringIds.eStrFt)
                }

                if (miles >= upperLimit) {
                    // >20 m - 1 m accuracy - M
                    distance = String.format("%d", (miles + 0.5f).toInt())
                    unit = getUIString(StringIds.eStrMi)
                } else if ((miles >= 1) && (miles < upperLimit)) {
                    // 1 - 20 m - 0.1 m accuracy - M
                    miles = (miles / 0.1f + 0.5f)
                    miles *= 0.1

                    distance = String.format("%.1f", miles)
                    unit = getUIString(StringIds.eStrMi)
                } else if ((miles >= fMilesThreshold) && (miles < 1)) {
                    // 0.25 - 1.00 m - 0.05 m accuracy - M
                    miles = (miles / 0.05f + 0.5f)
                    miles *= 0.05f

                    distance = String.format("%.2f", miles)
                    unit = getUIString(StringIds.eStrMi)
                } else if (miles < fMilesThreshold) {
                    if (yardsOrFeet >= 250) {
                        // 250 - 400 y - 25 y accuracy - YD
                        yardsOrFeet = ((yardsOrFeet + 12) / 25)
                        yardsOrFeet *= 25

                        distance = String.format("%d", yardsOrFeet)
                    } else if (yardsOrFeet >= 100 && yardsOrFeet < 250) {
                        // 100 - 250 y - 10 y accuracy - YD
                        yardsOrFeet = ((yardsOrFeet + 5) / 10)
                        yardsOrFeet *= 10

                        distance = String.format("%d", yardsOrFeet)
                    } else {
                        // 0 - 100 y - 5 m accuracy - YD
                        yardsOrFeet = ((yardsOrFeet + 2) / 5)
                        yardsOrFeet *= 5

                        distance = String.format("%d", yardsOrFeet)
                    }
                }
            }

            if (bCapitalizeUnit) {
                unit.toUpperCase(Locale.getDefault())
            }

            GEMSdkCall.execute {
                val decimalSeparator = CommonSettings().getDecimalSeparator()
                val groupingSeparator = CommonSettings().getDigitGroupSeparator()

                if (distance.indexOf(',') >= 0) {
                    distance.replace(',', decimalSeparator)
                } else // if (distance.find(".") >= 0)
                {
                    distance.replace('.', decimalSeparator)
                }

                var index: Int = distance.indexOf(decimalSeparator)
                if (index < 0) {
                    index = distance.length
                }

                for ((j, i) in (index - 1 downTo 0).withIndex()) {
                    val nRemaining: Int = j % 3
                    if (nRemaining == 0) {
                        distance.format(
                            "${
                                distance.substring(0, i)
                            }${groupingSeparator}${distance.subSequence(i, distance.length)}"
                        )
                    }
                }
            }

            return Pair(distance, unit)
        }

        fun getFormattedWaypointName(
            landmark: Landmark,
            bFillAddressInfo: Boolean = false
        ): String {
            return GEMSdkCall.execute {
                if (bFillAddressInfo) {
                    UtilUITexts.fillLandmarkAddressInfo(landmark)
                }

                var text = "" // result

                val landmarkAddress = landmark.getAddress() ?: return@execute ""

                var name = landmark.getName() ?: ""
                val streetName = landmarkAddress.getField(TAddressField.EStreetName) ?: ""
                val streetNumber = landmarkAddress.getField(TAddressField.EStreetNumber) ?: ""
                val settlement = landmarkAddress.getField(TAddressField.ESettlement) ?: ""
                val city = landmarkAddress.getField(TAddressField.ECity) ?: ""
                val county = landmarkAddress.getField(TAddressField.ECounty) ?: ""
                val state = landmarkAddress.getField(TAddressField.EState) ?: ""
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

                landmark.setName("")
                if ((streetName.isEmpty()) && !bNameEmpty) {
                    landmarkAddress.setField(unknownXYZ, TAddressField.EStreetName)
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

                landmark.setName(name)
                if (bRestoreStreetName) {
                    landmarkAddress.setField(streetName, TAddressField.EStreetName)
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

        fun getImageAsBitmap(
            iconId: Int,
            width: Int,
            height: Int
        ): Bitmap? {
            return GEMSdkCall.execute { Util.getImageIdAsBitmap(iconId, width, height) }
        }

        fun getImageAsBitmap(
            icon: Image?,
            width: Int,
            height: Int
        ): Bitmap? {
            return GEMSdkCall.execute { Util.createBitmap(icon, width, height) }
        }

        fun getImageIdAsBitmap(
            iconId: Int,
            width: Int,
            height: Int
        ): Bitmap? {
            return GEMSdkCall.execute { Util.getImageIdAsBitmap(iconId, width, height) }
        }

        fun getErrorMessage(nError: GEMError): String {
            return when (nError) {
                GEMError.KGeneral -> return getUIString(StringIds.eStrErrMsgKGeneral)
                GEMError.KActivation -> return getUIString(StringIds.eStrErrMsgKActivation)
                GEMError.KCancel -> return getUIString(StringIds.eStrErrMsgKCancel)
                GEMError.KNotSupported -> return getUIString(StringIds.eStrErrMsgKNotSupported)
                GEMError.KExist -> return getUIString(StringIds.eStrErrMsgKExist)
                GEMError.KIo -> return getUIString(StringIds.eStrErrMsgKIo)
                GEMError.KAccessDenied -> return getUIString(StringIds.eStrErrMsgKAccessDenied)
                GEMError.KReadonlyDrive -> return getUIString(StringIds.eStrErrMsgKReadonlyDrive)
                GEMError.KNoDiskSpace -> return getUIString(StringIds.eStrErrMsgKNoDiskSpace)
                GEMError.KInUse -> return getUIString(StringIds.eStrErrMsgKInUse)
                GEMError.KNotFound -> return getUIString(StringIds.eStrErrMsgKNotFound)
                GEMError.KOutOfRange -> return getUIString(StringIds.eStrErrMsgKOutOfRange)
                GEMError.KInvalidated -> return getUIString(StringIds.eStrErrMsgKInvalidated)
                GEMError.KNoMemory -> return getUIString(StringIds.eStrErrMsgKNoMemory)
                GEMError.KInvalidInput -> return getUIString(StringIds.eStrErrMsgKInvalidInput)
                GEMError.KReducedResult -> return getUIString(StringIds.eStrErrMsgKReducedResult)
                GEMError.KRequired -> return getUIString(StringIds.eStrErrMsgKRequired)
                GEMError.KNoRoute -> return getUIString(StringIds.eStrErrMsgKNoRoute)
                GEMError.KWaypointAccess -> return getUIString(StringIds.eStrErrMsgKWaypointAccess)
                GEMError.KRouteTooLong -> return getUIString(StringIds.eStrErrMsgKRouteTooLong)
                GEMError.KInternalAbort -> return getUIString(StringIds.eStrErrMsgKInternalAbort)
                GEMError.KConnection -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KNetworkFailed -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KNoConnection -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KConnectionRequired -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KSendFailed -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KRecvFailed -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KCouldNotStart -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KNetworkTimeout -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KNetworkCouldntResolveHost -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KNetworkCouldntResolveProxy -> return getUIString(StringIds.eStrErrMsgKNoConnection)
                GEMError.KNetworkCouldntResume -> return getUIString(StringIds.eStrErrMsgKNoConnection)
// 				GEMError.KNotLoggedIn->                  return getUIString(StringIds.eStrErrMsgKContentStoreNotOpened)
                GEMError.KSuspended -> return getUIString(StringIds.eStrErrMsgKSuspended)
                GEMError.KUpToDate -> return getUIString(StringIds.eStrErrMsgKUpToDate)
// 				GEMError.KResourceMissing->              return getUIString(StringIds.eStrErrMsgKResourceMissing)
                GEMError.KOperationTimeout -> return getUIString(StringIds.eStrErrMsgKOperationTimeout)
                else -> {
                    getUIString(StringIds.eStrUnknownErr)
                }
            }
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
}

class AppUtils {
    companion object {
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

        fun createBitmap(img: ByteArray?, width: Int, height: Int): Bitmap? {
            if (img == null || width <= 0 || height <= 0) {
                return null
            }

            val byteBuffer: ByteBuffer = ByteBuffer.wrap(img)
            byteBuffer.order(ByteOrder.nativeOrder())
            val buffer: IntBuffer = byteBuffer.asIntBuffer()
            val imgArray = IntArray(width * height)
            buffer.get(imgArray)
            val result = Bitmap.createBitmap(imgArray, width, height, Bitmap.Config.ARGB_8888)
            result.density = DisplayMetrics.DENSITY_MEDIUM
            return result
        }
    }
}
