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
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Insets
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowInsets
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.places.EAddressField
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.UtilUiTexts.getUIString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.*


// //// copied from android-kt
object Utils {
    fun getImageAspectRatio(nImageId: Int): Float {
        return SdkCall.execute {
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

    fun getFormattedWaypointName(
        landmark: Landmark,
        bFillAddressInfo: Boolean = false
    ): String {
        return SdkCall.execute {
            if (bFillAddressInfo) {
                UtilUITexts.fillLandmarkAddressInfo(landmark)
            }

            var text = "" // result

            val landmarkAddress = landmark.getAddress() ?: return@execute ""

            var name = landmark.getName() ?: ""
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

            landmark.setName("")
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

            landmark.setName(name)
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

    fun getImageAsBitmap(
        iconId: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        return SdkCall.execute { Util.getImageIdAsBitmap(iconId, width, height) }
    }

    fun getImageAsBitmap(
        icon: Image?,
        width: Int,
        height: Int
    ): Bitmap? {
        return SdkCall.execute { Util.createBitmap(icon, width, height) }
    }

    fun getImageIdAsBitmap(
        iconId: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        return SdkCall.execute { Util.getImageIdAsBitmap(iconId, width, height) }
    }

    fun getErrorMessage(nError: SdkError): String {
        return when (nError) {
            SdkError.General -> return getUIString(StringIds.eStrErrMsgKGeneral)
            SdkError.Activation -> return getUIString(StringIds.eStrErrMsgKActivation)
            SdkError.Cancel -> return getUIString(StringIds.eStrErrMsgKCancel)
            SdkError.NotSupported -> return getUIString(StringIds.eStrErrMsgKNotSupported)
            SdkError.Exist -> return getUIString(StringIds.eStrErrMsgKExist)
            SdkError.Io -> return getUIString(StringIds.eStrErrMsgKIo)
            SdkError.AccessDenied -> return getUIString(StringIds.eStrErrMsgKAccessDenied)
            SdkError.ReadonlyDrive -> return getUIString(StringIds.eStrErrMsgKReadonlyDrive)
            SdkError.NoDiskSpace -> return getUIString(StringIds.eStrErrMsgKNoDiskSpace)
            SdkError.InUse -> return getUIString(StringIds.eStrErrMsgKInUse)
            SdkError.NotFound -> return getUIString(StringIds.eStrErrMsgKNotFound)
            SdkError.OutOfRange -> return getUIString(StringIds.eStrErrMsgKOutOfRange)
            SdkError.Invalidated -> return getUIString(StringIds.eStrErrMsgKInvalidated)
            SdkError.NoMemory -> return getUIString(StringIds.eStrErrMsgKNoMemory)
            SdkError.InvalidInput -> return getUIString(StringIds.eStrErrMsgKInvalidInput)
            SdkError.ReducedResult -> return getUIString(StringIds.eStrErrMsgKReducedResult)
            SdkError.Required -> return getUIString(StringIds.eStrErrMsgKRequired)
            SdkError.NoRoute -> return getUIString(StringIds.eStrErrMsgKNoRoute)
            SdkError.WaypointAccess -> return getUIString(StringIds.eStrErrMsgKWaypointAccess)
            SdkError.RouteTooLong -> return getUIString(StringIds.eStrErrMsgKRouteTooLong)
            SdkError.InternalAbort -> return getUIString(StringIds.eStrErrMsgKInternalAbort)
            SdkError.Connection -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.NetworkFailed -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.NoConnection -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.ConnectionRequired -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.SendFailed -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.RecvFailed -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.CouldNotStart -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.NetworkTimeout -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.NetworkCouldntResolveHost -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.NetworkCouldntResolveProxy -> return getUIString(StringIds.eStrErrMsgKNoConnection)
            SdkError.NetworkCouldntResume -> return getUIString(StringIds.eStrErrMsgKNoConnection)
// 				SdkError.NotLoggedIn->                  return getUIString(StringIds.eStrErrMsgKContentStoreNotOpened)
            SdkError.Suspended -> return getUIString(StringIds.eStrErrMsgKSuspended)
            SdkError.UpToDate -> return getUIString(StringIds.eStrErrMsgKUpToDate)
// 				SdkError.ResourceMissing->              return getUIString(StringIds.eStrErrMsgKResourceMissing)
            SdkError.OperationTimeout -> return getUIString(StringIds.eStrErrMsgKOperationTimeout)
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

    fun getLandmarkIcon(value: Landmark, imgSizes: Int): Bitmap? {
        var iconId = value.getImage()?.getUid()?.toInt()
        if (iconId == SdkIcons.Other_Engine.LocationDetailsFavouritePushPin.value) {
            val extraInfo = value.getExtraInfo() ?: arrayListOf()
            var originalIconId = "original_icon_id="
            for (info in extraInfo) {
                if (info.startsWith(originalIconId)) {
                    originalIconId = info.substring(originalIconId.length)
                    iconId = originalIconId.toInt()
                    if (iconId > 0) {
                        return getImageAsBitmap(iconId, imgSizes, imgSizes)
                    }
                }
            }
        }

        return Util.createBitmap(value.getImage(), imgSizes, imgSizes)
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

